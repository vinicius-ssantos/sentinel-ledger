package io.github.vinicius.sentinel.integration.messaging;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Only imported by tests that set {@code sentinel.messaging.enabled=true}; the rest of the suite never starts a
 * broker container, matching #22's "RabbitMQ is not required for Phase 1 or Phase 2 tests" acceptance criterion.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MessagingTestcontainersConfiguration {

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitMqContainer() {
		return new RabbitMQContainer("rabbitmq:4-management-alpine");
	}
}
