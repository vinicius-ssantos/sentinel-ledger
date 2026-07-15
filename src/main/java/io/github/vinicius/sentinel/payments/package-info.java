/**
 * Owns payment intents, authorizations, captures, and refunds.
 */
@org.springframework.modulith.ApplicationModule(
	id = "payments",
	displayName = "Payments",
	allowedDependencies = { "money", "merchant", "ledger", "idempotency", "audit" }
)
package io.github.vinicius.sentinel.payments;
