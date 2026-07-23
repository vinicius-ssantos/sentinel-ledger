package io.github.vinicius.sentinel.integration.messaging.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The generic dispatch-side consumer proving the messaging layer's reliability properties (dedup, retry, DLQ).
 * Issue #23's webhook delivery is expected to become the real business action here, reusing this same inbox check;
 * for now, a first delivery is recorded and a redelivery is a no-op, with no further business effect.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.messaging", name = "enabled", havingValue = "true")
class OutboxEventListener {

	private static final Logger log = LoggerFactory.getLogger(OutboxEventListener.class);

	private final JdbcProcessedMessageGateway processedMessageGateway;

	OutboxEventListener(JdbcProcessedMessageGateway processedMessageGateway) {
		this.processedMessageGateway = processedMessageGateway;
	}

	@RabbitListener(queues = "${sentinel.messaging.queue:sentinel.events.q}", containerFactory = "messagingListenerContainerFactory")
	void onMessage(Message message) {
		String messageId = message.getMessageProperties().getMessageId();
		if (messageId == null || messageId.isBlank()) {
			throw new IllegalStateException("outbox message received without a message id");
		}

		boolean firstDelivery = processedMessageGateway.markProcessed(UUID.fromString(messageId));
		if (!firstDelivery) {
			log.debug("duplicate delivery of outbox message {} ignored", messageId);
			return;
		}

		log.info("processed outbox message {} (routingKey={})", messageId, message.getMessageProperties().getReceivedRoutingKey());
	}
}
