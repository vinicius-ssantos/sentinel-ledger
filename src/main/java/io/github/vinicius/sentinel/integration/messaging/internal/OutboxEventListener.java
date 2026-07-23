package io.github.vinicius.sentinel.integration.messaging.internal;

import io.github.vinicius.sentinel.webhooks.WebhookDeliveryId;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryQueryPort;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryRequest;
import io.github.vinicius.sentinel.webhooks.WebhookDispatchPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The dispatch-side consumer: it turns every outbox event into a signed webhook delivery attempt. Dedup is decided
 * by webhook delivery status, not mere message consumption -- gating on "have we seen this id" instead would make a
 * redelivery after a *failed* attempt a silent no-op, defeating the retry this listener exists to drive. A message
 * that isn't yet delivered is (re)dispatched; {@link JdbcProcessedMessageGateway} then records the completed,
 * successful processing for observability.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.messaging", name = "enabled", havingValue = "true")
class OutboxEventListener {

	private static final Logger log = LoggerFactory.getLogger(OutboxEventListener.class);

	private final JdbcProcessedMessageGateway processedMessageGateway;
	private final WebhookDispatchPort webhookDispatchPort;
	private final WebhookDeliveryQueryPort webhookDeliveryQueryPort;
	private final MeterRegistry meterRegistry;

	OutboxEventListener(
		JdbcProcessedMessageGateway processedMessageGateway,
		WebhookDispatchPort webhookDispatchPort,
		WebhookDeliveryQueryPort webhookDeliveryQueryPort,
		MeterRegistry meterRegistry
	) {
		this.processedMessageGateway = processedMessageGateway;
		this.webhookDispatchPort = webhookDispatchPort;
		this.webhookDeliveryQueryPort = webhookDeliveryQueryPort;
		this.meterRegistry = meterRegistry;
	}

	@RabbitListener(queues = "${sentinel.messaging.queue:sentinel.events.q}", containerFactory = "messagingListenerContainerFactory")
	void onMessage(Message message) {
		String messageId = message.getMessageProperties().getMessageId();
		if (messageId == null || messageId.isBlank()) {
			throw new IllegalStateException("outbox message received without a message id");
		}
		UUID id = UUID.fromString(messageId);
		WebhookDeliveryId deliveryId = new WebhookDeliveryId(id);

		if (webhookDeliveryQueryPort.isDelivered(deliveryId)) {
			log.debug("duplicate delivery of outbox message {} ignored (already delivered)", messageId);
			Counter.builder("sentinel.messaging.dispatch.duplicate")
				.description("Redeliveries of an outbox message that was already fully delivered")
				.register(meterRegistry)
				.increment();
			return;
		}

		String aggregateType = headerValue(message, "aggregateType");
		String aggregateId = headerValue(message, "aggregateId");
		String eventType = message.getMessageProperties().getReceivedRoutingKey();
		String payload = new String(message.getBody(), StandardCharsets.UTF_8);

		webhookDispatchPort.deliver(new WebhookDeliveryRequest(deliveryId, aggregateType, aggregateId, eventType, payload));

		processedMessageGateway.markProcessed(id);
	}

	private static String headerValue(Message message, String name) {
		Object value = message.getMessageProperties().getHeaders().get(name);
		return value == null ? null : value.toString();
	}
}
