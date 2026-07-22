package io.github.vinicius.sentinel.reconciliation;

public enum ReconciliationMismatchType {
	/** The provider's resolved authorization outcome disagrees with a payment intent's already-terminal local state. */
	AUTHORIZATION_OUTCOME_DIVERGENCE
}
