package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.idempotency.CanonicalRequestHasher;
import io.github.vinicius.sentinel.idempotency.IdempotencyAcquisition;
import io.github.vinicius.sentinel.idempotency.IdempotencyGateway;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.idempotency.StoredResponse;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.OptimisticLockException;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentDecision;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentState;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.payments.internal.web.PaymentIntentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * The two local transactions of the persist-call-persist authorization workflow. Kept in a separate bean from
 * {@link AuthorizePaymentIntentService} so the {@code @Transactional} boundaries are enforced through a real
 * Spring proxy: calling these methods via {@code this} from the same instance would silently skip the transaction
 * advice.
 */
@Service
class AuthorizePaymentIntentSteps {

	static final String AUTHORIZE_OPERATION = "payment-intent.authorize";

	private final PaymentIntentStore paymentIntentStore;
	private final AuthorizationAttemptStore attemptStore;
	private final IdempotencyGateway idempotencyGateway;
	private final ObjectMapper objectMapper;

	AuthorizePaymentIntentSteps(
		PaymentIntentStore paymentIntentStore,
		AuthorizationAttemptStore attemptStore,
		IdempotencyGateway idempotencyGateway,
		ObjectMapper objectMapper
	) {
		this.paymentIntentStore = paymentIntentStore;
		this.attemptStore = attemptStore;
		this.idempotencyGateway = idempotencyGateway;
		this.objectMapper = objectMapper;
	}

	@Transactional
	AuthorizationAttempt beginAuthorization(
		MerchantId merchantId, IdempotencyKey idempotencyKey, PaymentIntentId paymentIntentId, URI instance
	) {
		String requestHash = CanonicalRequestHasher.hash(
			AUTHORIZE_OPERATION, merchantId.value(), new CanonicalAuthorizePaymentIntentCommand(paymentIntentId.value().toString())
		);
		IdempotencyAcquisition acquisition = idempotencyGateway.acquire(merchantId.value(), AUTHORIZE_OPERATION, idempotencyKey, requestHash);

		if (acquisition instanceof IdempotencyAcquisition.Replayed replayed) {
			return AuthorizationAttempt.terminal(ApiResult.fromStoredResponse(replayed.response(), true));
		}
		if (acquisition instanceof IdempotencyAcquisition.KeyConflict) {
			return AuthorizationAttempt.terminal(problemResult(
				HttpStatus.CONFLICT, "idempotency-key-reused", "IDEMPOTENCY_KEY_REUSED",
				"Idempotency key cannot be reused for a different request",
				"Use a new key for a request with different business parameters.", instance, null
			));
		}
		if (acquisition instanceof IdempotencyAcquisition.InProgress) {
			return AuthorizationAttempt.terminal(problemResult(
				HttpStatus.CONFLICT, "idempotency-request-in-progress", "IDEMPOTENCY_REQUEST_IN_PROGRESS",
				"The original request is still being processed", "Retry after the indicated delay.", instance, 1
			));
		}
		if (acquisition instanceof IdempotencyAcquisition.RecoveryRequired recovery) {
			return recover(merchantId, idempotencyKey, recovery.resourceId());
		}

		return beginFreshAuthorization(merchantId, idempotencyKey, paymentIntentId, instance);
	}

	private AuthorizationAttempt beginFreshAuthorization(
		MerchantId merchantId, IdempotencyKey idempotencyKey, PaymentIntentId paymentIntentId, URI instance
	) {
		PaymentIntent paymentIntent = paymentIntentStore.findOwned(paymentIntentId, merchantId).orElse(null);
		if (paymentIntent == null) {
			return AuthorizationAttempt.terminal(failTerminal(merchantId, idempotencyKey, problemResult(
				HttpStatus.NOT_FOUND, "payment-intent-not-found", "PAYMENT_INTENT_NOT_FOUND",
				"Payment intent not found",
				"No payment intent exists for the authenticated merchant with this identifier.", instance, null
			)));
		}

		PaymentIntentDecision decision = paymentIntent.startAuthorization(Instant.now());
		if (!decision.wasApplied()) {
			return AuthorizationAttempt.terminal(failTerminal(merchantId, idempotencyKey, problemResult(
				HttpStatus.CONFLICT, "invalid-payment-transition", "INVALID_PAYMENT_TRANSITION",
				"Invalid payment intent transition", decision.detail(), instance, null
			)));
		}

		paymentIntentStore.save(paymentIntent);
		PspAttemptId attemptId = PspAttemptId.generate();
		attemptStore.recordPendingAttempt(paymentIntentId, attemptId);
		idempotencyGateway.markRecoveryRequired(merchantId.value(), AUTHORIZE_OPERATION, idempotencyKey, paymentIntentId.value().toString());

		return AuthorizationAttempt.fresh(attemptId, paymentIntent.amount());
	}

	private AuthorizationAttempt recover(MerchantId merchantId, IdempotencyKey idempotencyKey, String resourceId) {
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.fromString(resourceId));
		PaymentIntent paymentIntent = paymentIntentStore.findOwned(paymentIntentId, merchantId)
			.orElseThrow(() -> new IllegalStateException("recovery resource is missing: " + resourceId));

		boolean stillUncertain = paymentIntent.state() == PaymentIntentState.AUTHORIZATION_PENDING
			|| paymentIntent.state() == PaymentIntentState.AUTHORIZATION_UNKNOWN;
		if (stillUncertain) {
			PspAttemptId attemptId = attemptStore.findPendingAttempt(paymentIntentId)
				.orElseThrow(() -> new IllegalStateException("no pending PSP attempt recorded for " + paymentIntentId));
			return AuthorizationAttempt.recovering(attemptId, paymentIntent.amount());
		}

		ApiResult response = successResult(paymentIntent);
		idempotencyGateway.complete(merchantId.value(), AUTHORIZE_OPERATION, idempotencyKey, toStoredResponse(response));
		return AuthorizationAttempt.terminal(response);
	}

	@Transactional
	ApiResult resolveAuthorization(
		MerchantId merchantId,
		IdempotencyKey idempotencyKey,
		PaymentIntentId paymentIntentId,
		PspAttemptId attemptId,
		PspAuthorizationResult result,
		URI instance
	) {
		PaymentIntent paymentIntent = paymentIntentStore.findOwned(paymentIntentId, merchantId)
			.orElseThrow(() -> new IllegalStateException("payment intent disappeared mid-authorization: " + paymentIntentId));

		Instant now = Instant.now();
		PaymentIntentDecision decision = applyResult(paymentIntent, result, now);
		if (decision.wasApplied()) {
			try {
				paymentIntentStore.save(paymentIntent);
				attemptStore.recordResolution(paymentIntentId, attemptId, result, now);
			} catch (OptimisticLockException e) {
				paymentIntent = paymentIntentStore.findOwned(paymentIntentId, merchantId)
					.orElseThrow(() -> new IllegalStateException("payment intent disappeared mid-authorization: " + paymentIntentId));
			}
		}

		ApiResult response = successResult(paymentIntent);
		if (paymentIntent.state() != PaymentIntentState.AUTHORIZATION_UNKNOWN) {
			idempotencyGateway.complete(merchantId.value(), AUTHORIZE_OPERATION, idempotencyKey, toStoredResponse(response));
		}
		return response;
	}

	private static PaymentIntentDecision applyResult(PaymentIntent paymentIntent, PspAuthorizationResult result, Instant now) {
		if (result instanceof PspAuthorizationResult.Approved) {
			return paymentIntent.authorize(now);
		}
		if (result instanceof PspAuthorizationResult.Declined) {
			return paymentIntent.decline(now);
		}
		if (result instanceof PspAuthorizationResult.Unknown || result instanceof PspAuthorizationResult.RetryableFailure) {
			return paymentIntent.markAuthorizationUnknown(now);
		}
		if (result instanceof PspAuthorizationResult.PermanentFailure) {
			return paymentIntent.failAuthorization(now);
		}
		throw new IllegalArgumentException("unsupported PSP authorization result: " + result);
	}

	private ApiResult successResult(PaymentIntent paymentIntent) {
		HttpStatus status = paymentIntent.state() == PaymentIntentState.AUTHORIZATION_UNKNOWN
			? HttpStatus.ACCEPTED
			: HttpStatus.OK;
		String location = "/api/v1/payment-intents/" + paymentIntent.id().value();
		return new ApiResult(status.value(), MediaType.APPLICATION_JSON_VALUE, writeJson(PaymentIntentResponse.from(paymentIntent)), location, null, false);
	}

	private ApiResult failTerminal(MerchantId merchantId, IdempotencyKey idempotencyKey, ApiResult problem) {
		idempotencyGateway.failTerminal(merchantId.value(), AUTHORIZE_OPERATION, idempotencyKey, toStoredResponse(problem));
		return problem;
	}

	private static StoredResponse toStoredResponse(ApiResult result) {
		return new StoredResponse(result.status(), result.contentType(), result.body(), result.location());
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
