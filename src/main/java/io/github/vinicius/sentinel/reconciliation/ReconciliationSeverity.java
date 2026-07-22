package io.github.vinicius.sentinel.reconciliation;

public enum ReconciliationSeverity {
	/** Local state was still uncertain; no money has moved on this divergence. */
	LOW,
	/** Local state already captured or refunded funds inconsistent with the provider's evidence. */
	HIGH
}
