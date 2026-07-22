package io.github.vinicius.sentinel.payments;

import java.util.Optional;

public interface AuthorizationAttemptEvidencePort {

	/** The most recent PSP attempt associated with this payment intent's authorization, if any was ever begun. */
	Optional<PspAttemptId> lastAttemptId(PaymentIntentId paymentIntentId);
}
