/**
 * Owns the RabbitMQ topology and the broker-specific implementation of the outbox module's publisher port.
 */
@org.springframework.modulith.ApplicationModule(
	id = "integration.messaging",
	displayName = "Messaging Integration",
	allowedDependencies = { "outbox", "webhooks" }
)
package io.github.vinicius.sentinel.integration.messaging;
