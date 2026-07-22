package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.audit.AuditActor;
import io.github.vinicius.sentinel.audit.AuditActorType;
import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditGateway;
import io.github.vinicius.sentinel.payments.AuthorizationReconciliationPort;
import io.github.vinicius.sentinel.payments.OptimisticLockException;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentDecision;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentState;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * The reconciliation-triggered counterpart of {@link AuthorizePaymentIntentSteps#resolveAuthorization}: same safe
 * domain transition, applied outside any client request, so there is no idempotency record to complete.
 */
@Service
public class ReconciledAuthorizationResolver implements AuthorizationReconciliationPort {

	private static final String RECONCILE_OPERATION = "payment-intent.reconcile";

	private final PaymentIntentStore paymentIntentStore;
	private final AuditGateway auditGateway;

	ReconciledAuthorizationResolver(PaymentIntentStore paymentIntentStore, AuditGateway auditGateway) {
		this.paymentIntentStore = paymentIntentStore;
		this.auditGateway = auditGateway;
	}

	@Override
	@Transactional
	public PaymentIntent applyReconciledResult(PaymentIntentId paymentIntentId, PspAuthorizationResult result) {
		PaymentIntent paymentIntent = paymentIntentStore.findById(paymentIntentId)
			.orElseThrow(() -> new IllegalStateException("payment intent disappeared during reconciliation: " + paymentIntentId));

		if (paymentIntent.state() != PaymentIntentState.AUTHORIZATION_PENDING
			&& paymentIntent.state() != PaymentIntentState.AUTHORIZATION_UNKNOWN) {
			return paymentIntent;
		}

		Instant now = Instant.now();
		PaymentIntentDecision decision = applyResult(paymentIntent, result, now);
		if (!decision.wasApplied()) {
			return paymentIntent;
		}

		try {
			paymentIntentStore.save(paymentIntent);
		} catch (OptimisticLockException e) {
			return paymentIntentStore.findById(paymentIntentId)
				.orElseThrow(() -> new IllegalStateException("payment intent disappeared during reconciliation: " + paymentIntentId));
		}

		if (paymentIntent.state() != PaymentIntentState.AUTHORIZATION_UNKNOWN) {
			auditGateway.record(AuditEvent.record(
				new AuditActor(AuditActorType.SYSTEM, "reconciliation-sweep"),
				RECONCILE_OPERATION,
				"payment_intent",
				paymentIntentId.value().toString(),
				"reconciliation:" + paymentIntentId.value(),
				Map.of("state", paymentIntent.state().name()),
				now
			));
		}

		return paymentIntent;
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
}
