/**
 * Owns cross-cutting telemetry configuration and technical observability adapters.
 */
@org.springframework.modulith.ApplicationModule(
	id = "observability",
	displayName = "Observability",
	allowedDependencies = {
		"payments", "ledger", "reconciliation", "idempotency", "integration.psp", "merchant", "audit"
	}
)
package io.github.vinicius.sentinel.observability;
