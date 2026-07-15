/**
 * Owns persistent request identity, hashing, acquisition, and replayable outcomes.
 */
@org.springframework.modulith.ApplicationModule(
	id = "idempotency",
	displayName = "Idempotency",
	allowedDependencies = {}
)
package io.github.vinicius.sentinel.idempotency;
