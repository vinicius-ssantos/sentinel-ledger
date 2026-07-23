package io.github.vinicius.sentinel.integration.messaging;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.outbox.OutboxEventId;
import io.github.vinicius.sentinel.outbox.OutboxEventStatus;
import io.github.vinicius.sentinel.outbox.OutboxPublisherPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the full publish -> broker -> consume -> inbox path against a real RabbitMQ container. This is the
 * only test class in the repository that requires a broker; it is opt-in via {@code sentinel.messaging.enabled}
 * and its own Testcontainers configuration, so nothing else in the suite pays this cost.
 */
@SpringBootTest(properties = "sentinel.messaging.enabled=true")
@Import({TestcontainersConfiguration.class, MessagingTestcontainersConfiguration.class})
class RabbitMqMessagingIntegrationTests {

	@Autowired
	private OutboxPublisherPort outboxPublisherPort;

	@Autowired
	private MessagingQueryPort messagingQueryPort;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${sentinel.messaging.exchange}")
	private String exchange;

	@Test
	void publishingAnOutboxRecordIsConsumedAndRecordedInTheInboxExactlyOnce() {
		OutboxRecord record = fixture("payment.captured", "{\"a\":1}");

		outboxPublisherPort.publish(record);

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(inboxCount(record.id().value())).isEqualTo(1));
	}

	@Test
	void redeliveringTheSameMessageIdDoesNotDuplicateTheInboxEntry() {
		OutboxRecord record = fixture("payment.refunded", "{\"a\":2}");

		outboxPublisherPort.publish(record);
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(inboxCount(record.id().value())).isEqualTo(1));

		// Simulates a broker redelivery: a second, independent delivery carrying the identical message id.
		outboxPublisherPort.publish(record);
		await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(inboxCount(record.id().value())).isEqualTo(1));
	}

	@Test
	void aMessageWithoutAMessageIdRetriesThenLandsInTheDeadLetterQueueInsteadOfLoopingForever() {
		long depthBefore = messagingQueryPort.deadLetterQueueDepth();

		// OutboxEventListener rejects any message without an id -- our own publisher always sets one, so publishing
		// one directly (bypassing OutboxPublisherPort) is a deterministic, production-code way to trigger the
		// retry-then-dead-letter path without a test-only failure hook in the listener itself.
		rabbitTemplate.convertAndSend(
			exchange, "payment.poison", new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties())
		);

		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(messagingQueryPort.deadLetterQueueDepth()).isGreaterThan(depthBefore));
	}

	private long inboxCount(UUID messageId) {
		Long count = jdbcTemplate.queryForObject(
			"select count(*) from messaging_inbox_processed_messages where message_id = ?", Long.class, messageId
		);
		return count == null ? 0 : count;
	}

	private static OutboxRecord fixture(String eventType, String payload) {
		Instant now = Instant.now();
		return new OutboxRecord(
			OutboxEventId.generate(), "payment_intent", UUID.randomUUID().toString(), eventType, payload,
			OutboxEventStatus.CLAIMED, 0, null, now, now, null
		);
	}
}
