package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Tracks the PSP attempt associated with a payment intent's authorization and records resolved provider evidence.
 * Deliberately separate from {@link PaymentIntentStore}: this is infrastructure bookkeeping for the
 * persist-call-persist workflow, not part of the {@code PaymentIntent} domain aggregate.
 */
interface AuthorizationAttemptStore {

	void recordPendingAttempt(PaymentIntentId paymentIntentId, PspAttemptId attemptId);

	Optional<PspAttemptId> findPendingAttempt(PaymentIntentId paymentIntentId);

	void recordResolution(PaymentIntentId paymentIntentId, PspAttemptId attemptId, PspAuthorizationResult result, Instant occurredAt);

	/** Every resolved PSP evidence record for this payment intent, ordered {@code occurredAt} ascending. */
	List<ResolvedAuthorizationAttempt> findResolutions(PaymentIntentId paymentIntentId);
}
