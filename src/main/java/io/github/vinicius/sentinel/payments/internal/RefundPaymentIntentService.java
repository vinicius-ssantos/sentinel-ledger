package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.audit.AuditActor;
import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditGateway;
import io.github.vinicius.sentinel.idempotency.CanonicalRequestHasher;
import io.github.vinicius.sentinel.idempotency.IdempotencyAcquisition;
import io.github.vinicius.sentinel.idempotency.IdempotencyGateway;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.idempotency.StoredResponse;
import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.EntryDirection;
import io.github.vinicius.sentinel.ledger.LedgerEntry;
import io.github.vinicius.sentinel.ledger.LedgerPostingPort;
import io.github.vinicius.sentinel.ledger.LedgerTransaction;
import io.github.vinicius.sentinel.ledger.LedgerTransactionId;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.outbox.OutboxEvent;
import io.github.vinicius.sentinel.outbox.OutboxGateway;
import io.github.vinicius.sentinel.payments.OptimisticLockException;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentDecision;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.internal.web.PaymentIntentResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Refund mirrors capture: a single local transaction with no PSP call, so the payment state update and its
 * compensating ledger posting either both commit or both roll back. The ledger entries simply reverse capture's
 * direction (debit merchant-payable, credit psp-clearing-receivable); the original capture entries are untouched.
 */
@Service
public class RefundPaymentIntentService {

	static final String REFUND_OPERATION = "payment-intent.refund";

	private final PaymentIntentStore paymentIntentStore;
	private final IdempotencyGateway idempotencyGateway;
	private final LedgerPostingPort ledgerPostingPort;
	private final AuditGateway auditGateway;
	private final OutboxGateway outboxGateway;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	RefundPaymentIntentService(
		PaymentIntentStore paymentIntentStore,
		IdempotencyGateway idempotencyGateway,
		LedgerPostingPort ledgerPostingPort,
		AuditGateway auditGateway,
		OutboxGateway outboxGateway,
		ObjectMapper objectMapper,
		MeterRegistry meterRegistry
	) {
		this.paymentIntentStore = paymentIntentStore;
		this.idempotencyGateway = idempotencyGateway;
		this.ledgerPostingPort = ledgerPostingPort;
		this.auditGateway = auditGateway;
		this.outboxGateway = outboxGateway;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
	}

	@Transactional
	public ApiResult refund(
		MerchantId merchantId,
		IdempotencyKey idempotencyKey,
		PaymentIntentId paymentIntentId,
		String amountInMinorUnits,
		String currencyCode,
		URI instance
	) {
		String requestHash = CanonicalRequestHasher.hash(
			REFUND_OPERATION, merchantId.value(),
			new CanonicalRefundPaymentIntentCommand(paymentIntentId.value().toString(), amountInMinorUnits, currencyCode)
		);
		IdempotencyAcquisition acquisition = idempotencyGateway.acquire(merchantId.value(), REFUND_OPERATION, idempotencyKey, requestHash);

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
				"The original request is still being processed", "Retry after the indicated delay.", instance, 1
			);
		}
		if (acquisition instanceof IdempotencyAcquisition.RecoveryRequired) {
			// Refund never leaves an external call in flight, so it never marks RECOVERY_REQUIRED itself; seeing
			// it here means a durable resource exists without a terminal response, which should be unreachable.
			throw new IllegalStateException("unexpected RECOVERY_REQUIRED for a single-transaction refund command");
		}

		PaymentIntent paymentIntent = paymentIntentStore.findOwned(paymentIntentId, merchantId).orElse(null);
		if (paymentIntent == null) {
			return failTerminal(merchantId, idempotencyKey, problemResult(
				HttpStatus.NOT_FOUND, "payment-intent-not-found", "PAYMENT_INTENT_NOT_FOUND",
				"Payment intent not found",
				"No payment intent exists for the authenticated merchant with this identifier.", instance, null
			));
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
				"Invalid refund request", "amountInMinorUnits must be a positive integer.", instance, null
			));
		}

		PaymentIntentDecision decision = paymentIntent.refund(amount, Instant.now());
		if (!decision.wasApplied()) {
			return failTerminal(merchantId, idempotencyKey, problemForDeniedRefund(decision, instance));
		}

		try {
			paymentIntentStore.save(paymentIntent);
		} catch (OptimisticLockException e) {
			return failTerminal(merchantId, idempotencyKey, problemResult(
				HttpStatus.CONFLICT, "concurrent-payment-modification", "CONCURRENT_PAYMENT_MODIFICATION",
				"Payment intent was concurrently modified",
				"Another request captured or refunded this payment intent first; retry with a new idempotency key.",
				instance, null
			));
		}

		AccountId receivable = AccountId.pspClearingReceivable(Currency.BRL);
		AccountId payable = AccountId.merchantPayable(paymentIntent.merchantId().value(), Currency.BRL);
		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(),
			"refund:" + paymentIntentId.value() + ":" + idempotencyKey.value(),
			Currency.BRL,
			List.of(
				new LedgerEntry(payable, EntryDirection.DEBIT, amount),
				new LedgerEntry(receivable, EntryDirection.CREDIT, amount)
			),
			Instant.now()
		);
		ledgerPostingPort.post(transaction);
		refundResultCounter("SUCCESS", null).increment();

		auditGateway.record(AuditEvent.record(
			AuditActor.merchant(merchantId.value().toString()),
			REFUND_OPERATION,
			"payment_intent",
			paymentIntentId.value().toString(),
			idempotencyKey.value(),
			Map.of(
				"state", paymentIntent.state().name(),
				"refundedAmountInMinorUnits", amount.amountInMinorUnitsText(),
				"currency", amount.currency().code()
			),
			Instant.now()
		));

		outboxGateway.enqueue(OutboxEvent.enqueue(
			"payment_intent",
			paymentIntentId.value().toString(),
			"payment.refunded",
			writeJson(Map.of(
				"paymentIntentId", paymentIntentId.value().toString(),
				"merchantId", merchantId.value().toString(),
				"refundedAmountInMinorUnits", amount.amountInMinorUnitsText(),
				"currency", amount.currency().code(),
				"state", paymentIntent.state().name()
			)),
			Instant.now()
		));

		StoredResponse response = new StoredResponse(
			HttpStatus.OK.value(),
			MediaType.APPLICATION_JSON_VALUE,
			writeJson(PaymentIntentResponse.from(paymentIntent)),
			"/api/v1/payment-intents/" + paymentIntent.id().value()
		);
		idempotencyGateway.complete(merchantId.value(), REFUND_OPERATION, idempotencyKey, response);
		return ApiResult.fromStoredResponse(response, false);
	}

	private ApiResult problemForDeniedRefund(PaymentIntentDecision decision, URI instance) {
		refundResultCounter("DENIED", decision.errorCode().name()).increment();
		return switch (decision.errorCode()) {
			case REFUND_EXCEEDS_CAPTURED_AMOUNT -> problemResult(
				HttpStatus.CONFLICT, "refund-limit-exceeded", "REFUND_LIMIT_EXCEEDED",
				"Refund exceeds the remaining refundable amount", decision.detail(), instance, null
			);
			default -> problemResult(
				HttpStatus.CONFLICT, "invalid-payment-transition", "INVALID_PAYMENT_TRANSITION",
				"Invalid payment intent transition", decision.detail(), instance, null
			);
		};
	}

	/**
	 * Tagged by outcome and, when denied, the fixed {@link io.github.vinicius.sentinel.payments.PaymentIntentErrorCode}
	 * -- never a payment or merchant identifier, so cardinality cannot grow with traffic.
	 */
	private Counter refundResultCounter(String outcome, String reason) {
		return Counter.builder("sentinel.payments.refund.result")
			.description("Refund requests resolved, by outcome and (when denied) reason")
			.tag("outcome", outcome)
			.tag("reason", reason == null ? "" : reason)
			.register(meterRegistry);
	}

	private ApiResult failTerminal(MerchantId merchantId, IdempotencyKey idempotencyKey, ApiResult problem) {
		idempotencyGateway.failTerminal(
			merchantId.value(),
			REFUND_OPERATION,
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
