package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.audit.AuditActor;
import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditGateway;
import io.github.vinicius.sentinel.idempotency.CanonicalRequestHasher;
import io.github.vinicius.sentinel.idempotency.IdempotencyAcquisition;
import io.github.vinicius.sentinel.idempotency.IdempotencyGateway;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.idempotency.StoredResponse;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.internal.web.PaymentIntentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Service
public class PaymentIntentCommandService {

	static final String CREATE_OPERATION = "payment-intent.create";

	private final PaymentIntentStore paymentIntentStore;
	private final IdempotencyGateway idempotencyGateway;
	private final AuditGateway auditGateway;
	private final ObjectMapper objectMapper;

	PaymentIntentCommandService(
		PaymentIntentStore paymentIntentStore,
		IdempotencyGateway idempotencyGateway,
		AuditGateway auditGateway,
		ObjectMapper objectMapper
	) {
		this.paymentIntentStore = paymentIntentStore;
		this.idempotencyGateway = idempotencyGateway;
		this.auditGateway = auditGateway;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public ApiResult create(
		MerchantId merchantId,
		IdempotencyKey idempotencyKey,
		String amountInMinorUnits,
		String currencyCode,
		URI instance
	) {
		String requestHash = CanonicalRequestHasher.hash(
			CREATE_OPERATION, merchantId.value(), new CanonicalCreatePaymentIntentCommand(amountInMinorUnits, currencyCode)
		);

		IdempotencyAcquisition acquisition = idempotencyGateway.acquire(merchantId.value(), CREATE_OPERATION, idempotencyKey, requestHash);

		if (acquisition instanceof IdempotencyAcquisition.Replayed replayed) {
			return ApiResult.fromStoredResponse(replayed.response(), true);
		}
		if (acquisition instanceof IdempotencyAcquisition.KeyConflict) {
			return problemResult(
				HttpStatus.CONFLICT, "idempotency-key-reused", "IDEMPOTENCY_KEY_REUSED",
				"Idempotency key cannot be reused for a different request",
				"Use a new key for a request with different business parameters.", instance, null
			);
		}
		if (acquisition instanceof IdempotencyAcquisition.InProgress) {
			return problemResult(
				HttpStatus.CONFLICT, "idempotency-request-in-progress", "IDEMPOTENCY_REQUEST_IN_PROGRESS",
				"The original request is still being processed",
				"Retry after the indicated delay.", instance, 1
			);
		}

		if (!Currency.BRL.code().equalsIgnoreCase(currencyCode)) {
			return failTerminal(merchantId, idempotencyKey, problemResult(
				HttpStatus.UNPROCESSABLE_ENTITY, "unsupported-currency", "UNSUPPORTED_CURRENCY",
				"Unsupported currency", "The MVP accepts BRL only.", instance, null
			));
		}

		Money amount;
		try {
			amount = Money.positive(Long.parseLong(amountInMinorUnits), Currency.BRL);
		} catch (IllegalArgumentException e) {
			return failTerminal(merchantId, idempotencyKey, problemResult(
				HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST",
				"Invalid payment intent request", "amountInMinorUnits must be a positive integer.", instance, null
			));
		}

		PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentId.generate(), merchantId, amount, Instant.now());
		paymentIntentStore.save(paymentIntent);

		auditGateway.record(AuditEvent.record(
			AuditActor.merchant(merchantId.value().toString()),
			CREATE_OPERATION,
			"payment_intent",
			paymentIntent.id().value().toString(),
			idempotencyKey.value(),
			Map.of(
				"state", paymentIntent.state().name(),
				"amountInMinorUnits", paymentIntent.amount().amountInMinorUnitsText(),
				"currency", paymentIntent.amount().currency().code()
			),
			Instant.now()
		));

		StoredResponse response = new StoredResponse(
			HttpStatus.CREATED.value(),
			MediaType.APPLICATION_JSON_VALUE,
			writeJson(PaymentIntentResponse.from(paymentIntent)),
			"/api/v1/payment-intents/" + paymentIntent.id().value()
		);
		idempotencyGateway.complete(merchantId.value(), CREATE_OPERATION, idempotencyKey, response);
		return ApiResult.fromStoredResponse(response, false);
	}

	private ApiResult failTerminal(MerchantId merchantId, IdempotencyKey idempotencyKey, ApiResult problem) {
		idempotencyGateway.failTerminal(
			merchantId.value(),
			CREATE_OPERATION,
			idempotencyKey,
			new StoredResponse(problem.status(), problem.contentType(), problem.body(), problem.location())
		);
		return problem;
	}

	private ApiResult problemResult(
		HttpStatus status, String typeSlug, String code, String title, String detail, URI instance, Integer retryAfterSeconds
	) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("https://sentinel-ledger.dev/problems/" + typeSlug));
		problem.setInstance(instance);
		problem.setProperty("code", code);
		return new ApiResult(status.value(), MediaType.APPLICATION_PROBLEM_JSON_VALUE, writeJson(problem), null, retryAfterSeconds, false);
	}

	private String writeJson(Object value) {
		return objectMapper.writeValueAsString(value);
	}
}
