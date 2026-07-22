package io.github.vinicius.sentinel.reconciliation;

/** Matches the lifecycle documented in docs/DOMAIN_MODEL.md's ReconciliationCase section. */
public enum ReconciliationCaseStatus {
	OPEN,
	INVESTIGATING,
	RESOLVED,
	IGNORED_WITH_REASON
}
