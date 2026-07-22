/**
 * Owns mismatch detection, evidence, cases, and audited resolutions.
 */
@org.springframework.modulith.ApplicationModule(
	id = "reconciliation",
	displayName = "Reconciliation",
	allowedDependencies = { "payments", "ledger", "integration.psp", "audit", "money" }
)
package io.github.vinicius.sentinel.reconciliation;
