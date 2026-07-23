package io.github.vinicius.sentinel.integration.messaging;

import com.sun.net.httpserver.HttpServer;
import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.outbox.OutboxEventId;
import io.github.vinicius.sentinel.outbox.OutboxEventStatus;
import io.github.vinicius.sentinel.outbox.OutboxPublisherPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryId;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryQueryPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the full publish -> broker -> consume -> webhook path against a real RabbitMQ container and a plain
 * JDK HttpServer standing in for the merchant endpoint. This is the only test class in the repository that
 * requires a broker; it is opt-in via {@code sentinel.messaging.enabled} and its own Testcontainers configuration,
 * so nothing else in the suite pays this cost. Retry timing is shortened via properties so the dead-letter test
 * doesn't need to wait out the production backoff schedule.
 */
@SpringBootTest(properties = {
	"sentinel.messaging.enabled=true",
	"sentinel.messaging.max-attempts=2",
	"sentinel.messaging.initial-interval=PT0.1S",
	"sentinel.messaging.max-interval=PT0.3S"
})
@Import({TestcontainersConfiguration.class, MessagingTestcontainersConfiguration.class})
class RabbitMqMessagingIntegrationTests {

	private static HttpServer server;
	private static final Deque<Integer> nextStatuses = new ArrayDeque<>();
	private static final CopyOnWriteArrayList<String> receivedDeliveryIds = new CopyOnWriteArrayList<>();

	@Autowired
	private OutboxPublisherPort outboxPublisherPort;

	@Autowired
	private MessagingQueryPort messagingQueryPort;

	@Autowired
	private WebhookDeliveryQueryPort webhookDeliveryQueryPort;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${sentinel.messaging.exchange}")
	private String exchange;

	@BeforeAll
	static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/hook", exchange -> {
			exchange.getRequestBody().readAllBytes();
			receivedDeliveryIds.add(exchange.getRequestHeaders().getFirst("Sentinel-Webhook-Id"));
			Integer status = nextStatuses.pollFirst();
			exchange.sendResponseHeaders(status == null ? 200 : status, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterAll
	static void stopServer() {
		server.stop(0);
	}

	@BeforeEach
	void reset() {
		receivedDeliveryIds.clear();
		nextStatuses.clear();
	}

	@DynamicPropertySource
	static void webhookUrl(DynamicPropertyRegistry registry) {
		registry.add("sentinel.webhook.url", () -> "http://localhost:" + server.getAddress().getPort() + "/hook");
	}

	@Test
	void publishingAnOutboxRecordIsConsumedAndDeliveredAsAWebhookExactlyOnce() {
		OutboxRecord record = fixture("payment.captured", "{\"a\":1}");

		outboxPublisherPort.publish(record);

		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(inboxCount(record.id().value())).isEqualTo(1));
		assertThat(webhookDeliveryQueryPort.isDelivered(new WebhookDeliveryId(record.id().value()))).isTrue();
		assertThat(receivedDeliveryIds).containsExactly(record.id().value().toString());
	}

	@Test
	void redeliveringTheSameMessageIdDoesNotDuplicateTheInboxEntryOrReNotifyTheMerchant() {
		OutboxRecord record = fixture("payment.refunded", "{\"a\":2}");

		outboxPublisherPort.publish(record);
		await().atMost(Duration.ofSeconds(10))
			.untilAsserted(() -> assertThat(inboxCount(record.id().value())).isEqualTo(1));
		assertThat(receivedDeliveryIds).hasSize(1);

		// Simulates a broker redelivery: a second, independent delivery carrying the identical message id.
		outboxPublisherPort.publish(record);
		await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
			assertThat(inboxCount(record.id().value())).isEqualTo(1);
			assertThat(receivedDeliveryIds).hasSize(1);
		});
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

	@Test
	void aPersistentlyFailingWebhookRetriesThenIsMarkedFailedAndDeadLettered() {
		for (int i = 0; i < 10; i++) {
			nextStatuses.add(500);
		}
		OutboxRecord record = fixture("payment.captured", "{\"a\":3}");
		long dlqDepthBefore = messagingQueryPort.deadLetterQueueDepth();

		outboxPublisherPort.publish(record);

		await().atMost(Duration.ofSeconds(30))
			.untilAsserted(() -> assertThat(messagingQueryPort.deadLetterQueueDepth()).isGreaterThan(dlqDepthBefore));
		assertThat(webhookDeliveryQueryPort.isDelivered(new WebhookDeliveryId(record.id().value()))).isFalse();
		String status = jdbcTemplate.queryForObject(
			"select status from webhook_deliveries where id = ?", String.class, record.id().value()
		);
		assertThat(status).isEqualTo("FAILED");
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
