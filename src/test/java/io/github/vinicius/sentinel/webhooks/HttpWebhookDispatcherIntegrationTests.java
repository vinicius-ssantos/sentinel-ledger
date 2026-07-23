package io.github.vinicius.sentinel.webhooks;

import com.sun.net.httpserver.HttpServer;
import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses a plain JDK HttpServer as the "merchant endpoint" instead of a mocking library the project doesn't already
 * depend on -- it only needs to record requests and return a configurable status code.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class HttpWebhookDispatcherIntegrationTests {

	private static HttpServer server;
	private static final Deque<Integer> nextStatuses = new ArrayDeque<>();
	private static final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
	private static final String SECRET = "sentinel-dev-webhook-secret";

	@Autowired
	private WebhookDispatchPort webhookDispatchPort;

	@Autowired
	private WebhookDeliveryQueryPort webhookDeliveryQueryPort;

	@BeforeAll
	static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/hook", exchange -> {
			byte[] body = exchange.getRequestBody().readAllBytes();
			requests.add(new RecordedRequest(
				new String(body, StandardCharsets.UTF_8),
				exchange.getRequestHeaders().getFirst("Sentinel-Webhook-Id"),
				exchange.getRequestHeaders().getFirst("Sentinel-Webhook-Event"),
				exchange.getRequestHeaders().getFirst("Sentinel-Signature")
			));
			Integer status = nextStatuses.pollFirst();
			int code = status == null ? 200 : status;
			exchange.sendResponseHeaders(code, -1);
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
		requests.clear();
		nextStatuses.clear();
	}

	@DynamicPropertySource
	static void webhookUrl(DynamicPropertyRegistry registry) {
		registry.add("sentinel.webhook.url", () -> "http://localhost:" + server.getAddress().getPort() + "/hook");
		registry.add("sentinel.webhook.secret", () -> SECRET);
	}

	@Test
	void deliversASignedRequestAndMarksItDelivered() {
		WebhookDeliveryRequest request = fixture("{\"a\":1}");

		webhookDispatchPort.deliver(request);

		assertThat(requests).hasSize(1);
		RecordedRequest received = requests.getFirst();
		assertThat(received.body()).isEqualTo("{\"a\":1}");
		assertThat(received.deliveryId()).isEqualTo(request.id().value().toString());
		assertThat(received.eventType()).isEqualTo(request.eventType());
		WebhookSignatureVerifier.Result verification = WebhookSignatureVerifier.verify(
			received.signature(), received.deliveryId(), received.body(), List.of(SECRET), Instant.now(), Duration.ofMinutes(1)
		);
		assertThat(verification.valid()).isTrue();

		assertThat(webhookDeliveryQueryPort.isDelivered(request.id())).isTrue();
		WebhookDeliveryRecord record = onlyRecordFor(request);
		assertThat(record.status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
		assertThat(record.attemptCount()).isEqualTo(1);
	}

	@Test
	void retryingAfterAFailurePreservesTheSameDeliveryIdAndEventuallySucceeds() {
		WebhookDeliveryRequest request = fixture("{\"a\":2}");
		nextStatuses.add(500);

		assertThatThrownBy(() -> webhookDispatchPort.deliver(request)).isInstanceOf(WebhookDeliveryFailedException.class);
		WebhookDeliveryRecord afterFailure = onlyRecordFor(request);
		assertThat(afterFailure.status()).isEqualTo(WebhookDeliveryStatus.PENDING);
		assertThat(afterFailure.attemptCount()).isEqualTo(1);
		assertThat(afterFailure.lastError()).isNotNull();

		webhookDispatchPort.deliver(request);
		WebhookDeliveryRecord afterRetry = onlyRecordFor(request);
		assertThat(afterRetry.status()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
		assertThat(afterRetry.attemptCount()).isEqualTo(2);

		assertThat(requests).hasSize(2);
		assertThat(requests.get(0).deliveryId()).isEqualTo(requests.get(1).deliveryId());
	}

	@Test
	void markExhaustedTransitionsAPendingDeliveryToFailed() {
		WebhookDeliveryRequest request = fixture("{\"a\":3}");
		nextStatuses.add(500);
		assertThatThrownBy(() -> webhookDispatchPort.deliver(request)).isInstanceOf(WebhookDeliveryFailedException.class);

		webhookDispatchPort.markExhausted(request.id(), "retry budget exhausted");

		WebhookDeliveryRecord record = onlyRecordFor(request);
		assertThat(record.status()).isEqualTo(WebhookDeliveryStatus.FAILED);
		assertThat(record.lastError()).isEqualTo("retry budget exhausted");
	}

	private WebhookDeliveryRecord onlyRecordFor(WebhookDeliveryRequest request) {
		List<WebhookDeliveryRecord> matches = new ArrayList<>(
			webhookDeliveryQueryPort.findByAggregate(request.aggregateType(), request.aggregateId())
		);
		matches.removeIf(r -> !r.id().equals(request.id()));
		assertThat(matches).hasSize(1);
		return matches.getFirst();
	}

	private static WebhookDeliveryRequest fixture(String payload) {
		return new WebhookDeliveryRequest(
			new WebhookDeliveryId(UUID.randomUUID()), "payment_intent", UUID.randomUUID().toString(), "payment.captured", payload
		);
	}

	private record RecordedRequest(String body, String deliveryId, String eventType, String signature) {
	}
}
