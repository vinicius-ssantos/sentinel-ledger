/**
 * Owns payment intents, authorizations, captures, and refunds.
 */
@org.springframework.modulith.ApplicationModule(
	id = "payments",
	displayName = "Payments",
	allowedDependencies = { "merchant", "integration.psp", "ledger", "idempotency", "audit" }
)
package io.github.vinicius.sentinel.payments;
