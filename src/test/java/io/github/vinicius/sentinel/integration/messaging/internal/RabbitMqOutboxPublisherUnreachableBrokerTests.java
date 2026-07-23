package io.github.vinicius.sentinel.integration.messaging.internal;

import io.github.vinicius.sentinel.outbox.OutboxEventId;
import io.github.vinicius.sentinel.outbox.OutboxEventStatus;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No Testcontainers broker here on purpose: pointing at a port nothing listens on is what proves OUT-001 survives a
 * broker outage. {@code publish} must throw rather than silently succeed, so the outbox worker leaves the record
 * claimed for retry (io.github.vinicius.sentinel.outbox.internal.OutboxDispatchWorkerIntegrationTests already
 * proves that a thrown publish exception requeues the record; this test proves this adapter actually throws).
 */
class RabbitMqOutboxPublisherUnreachableBrokerTests {

	private CachingConnectionFactory connectionFactory;

	@AfterEach
	void closeConnectionFactory() {
		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}

	@Test
	void publishThrowsInsteadOfSilentlySucceedingWhenTheBrokerIsUnreachable() {
		connectionFactory = new CachingConnectionFactory("localhost", 1);
		connectionFactory.setConnectionTimeout(500);
		connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
		connectionFactory.setPublisherReturns(true);

		RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
		MessagingProperties properties = new MessagingProperties(
			true, "sentinel.events", "sentinel.events.q", "sentinel.events.dlx", "sentinel.events.dlq",
			5, Duration.ofMillis(100), 2.0, Duration.ofSeconds(1), 1000
		);
		RabbitMqOutboxPublisher publisher = new RabbitMqOutboxPublisher(rabbitTemplate, properties);
		OutboxRecord record = new OutboxRecord(
			OutboxEventId.generate(), "payment_intent", UUID.randomUUID().toString(), "payment.captured", "{}",
			OutboxEventStatus.CLAIMED, 0, null, Instant.now(), Instant.now(), null
		);

		assertThatThrownBy(() -> publisher.publish(record)).isInstanceOf(AmqpException.class);
	}
}
