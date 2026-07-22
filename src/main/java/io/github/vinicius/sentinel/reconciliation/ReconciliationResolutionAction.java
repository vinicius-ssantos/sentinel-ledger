package io.github.vinicius.sentinel.reconciliation;

public enum ReconciliationResolutionAction {
	/** The divergence needed no financial correction; recorded for evidence only. Case becomes RESOLVED. */
	ACKNOWLEDGE_NO_ACTION,
	/** Posts a new compensating ledger transaction reversing the inconsistent captured/refunded amount. Case becomes RESOLVED. */
	COMPENSATE,
	/** Administratively dismissed without a fix (for example, a confirmed false positive). Case becomes IGNORED_WITH_REASON. */
	IGNORE
}
