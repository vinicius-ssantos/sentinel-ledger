package io.github.vinicius.sentinel.payments;

/**
 * Provider-neutral contract the payment domain uses to request authorization and recover its status. Implementations
 * (the simulated adapter today, a real gateway integration later) live outside the {@code payments} module and must
 * never leak provider-specific types across this boundary.
 */
public interface PspAuthorizationPort {

	/**
	 * Requests authorization for the given attempt. A transport timeout or ambiguous response must be reported as
	 * {@link PspAuthorizationResult.Unknown}, never guessed as {@link PspAuthorizationResult.Declined}.
	 */
	PspAuthorizationResult authorize(PspAuthorizationRequest request);

	/**
	 * Looks up the current status of a previously attempted authorization, keyed by the caller-generated
	 * {@link PspAttemptId} rather than a provider-assigned reference, since that reference may be exactly what was
	 * lost in a timeout-after-processing scenario.
	 */
	PspAuthorizationResult checkStatus(PspAttemptId attemptId);
}
