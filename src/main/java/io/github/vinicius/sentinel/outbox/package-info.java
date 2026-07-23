/**
 * Owns the transactional outbox: publication intents enqueued in the same local transaction as the business
 * change they describe, and their claim/publish/complete lifecycle.
 */
@org.springframework.modulith.ApplicationModule(
	id = "outbox",
	displayName = "Outbox",
	allowedDependencies = {}
)
package io.github.vinicius.sentinel.outbox;
