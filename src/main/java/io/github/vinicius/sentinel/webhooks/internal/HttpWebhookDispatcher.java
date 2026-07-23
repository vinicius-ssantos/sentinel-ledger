package io.github.vinicius.sentinel.webhooks.internal;

import io.github.vinicius.sentinel.webhooks.WebhookDeliveryFailedException;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryId;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryRequest;
import io.github.vinicius.sentinel.webhooks.WebhookDispatchPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;

/**
 * A failed send is recorded here and rethrown unchanged in kind (never swallowed): the messaging consumer that
 * calls this port is expected to let the exception propagate so its own bounded retry/dead-letter policy governs
 * the retry, rather than this module retrying internally and duplicating that policy.
 */
@Component
@EnableConfigurationProperties(WebhookSubscriptionProperties.class)
class HttpWebhookDispatcher implements WebhookDispatchPort {

	private final RestClient restClient;
	private final JdbcWebhookDeliveryGateway gateway;
	private final WebhookSubscriptionProperties properties;
	private final MeterRegistry meterRegistry;

	HttpWebhookDispatcher(
		RestClient.Builder restClientBuilder, JdbcWebhookDeliveryGateway gateway, WebhookSubscriptionProperties properties,
		MeterRegistry meterRegistry
	) {
		this.restClient = restClientBuilder.build();
		this.gateway = gateway;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void deliver(WebhookDeliveryRequest request) {
		gateway.ensureRecorded(request);

		Instant now = Instant.now();
		String signature = WebhookSigner.header(now, request.id(), request.payload(), properties.secret());

		try {
			restClient.post()
				.uri(properties.url())
				.contentType(MediaType.APPLICATION_JSON)
				.header("Sentinel-Webhook-Id", request.id().value().toString())
				.header("Sentinel-Webhook-Event", request.eventType())
				.header("Sentinel-Signature", signature)
				.body(request.payload())
				.retrieve()
				.toBodilessEntity();
			gateway.recordAttempt(request.id(), true, null);
			deliveryResultCounter("DELIVERED").increment();
		} catch (RestClientException e) {
			gateway.recordAttempt(request.id(), false, safeMessage(e));
			deliveryResultCounter("FAILED").increment();
			throw new WebhookDeliveryFailedException("webhook delivery failed for " + request.id().value(), e);
		}
	}

	private Counter deliveryResultCounter(String result) {
		return Counter.builder("sentinel.webhooks.delivery.result")
			.description("Webhook delivery attempts, by outcome")
			.tag("result", result)
			.register(meterRegistry);
	}

	@Override
	public void markExhausted(WebhookDeliveryId id, String reason) {
		gateway.markExhausted(id, reason);
	}

	private static String safeMessage(Exception e) {
		String message = e.getMessage();
		return message == null ? e.getClass().getSimpleName() : message;
	}
}
