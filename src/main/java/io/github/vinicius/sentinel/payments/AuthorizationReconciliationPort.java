package io.github.vinicius.sentinel.payments;

public interface AuthorizationReconciliationPort {

	/**
	 * Applies freshly observed provider evidence to a payment intent that is still {@code AUTHORIZATION_PENDING}
	 * or {@code AUTHORIZATION_UNKNOWN} — the same safe transition {@code resolveAuthorization} applies when a
	 * client retries, but triggered by reconciliation instead, with no idempotency record to complete since there
	 * is no originating client request in this context. A no-op returning the current, unchanged payment intent
	 * when the state is no longer uncertain: reconciliation callers must not use this to overwrite an
	 * already-resolved outcome, since that requires an operator-reviewed {@code ReconciliationCase} instead.
	 */
	PaymentIntent applyReconciledResult(PaymentIntentId paymentIntentId, PspAuthorizationResult result);
}
