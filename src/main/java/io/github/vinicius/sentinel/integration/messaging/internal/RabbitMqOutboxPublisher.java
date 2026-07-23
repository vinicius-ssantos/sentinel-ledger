package io.github.vinicius.sentinel.integration.messaging.internal;

import io.github.vinicius.sentinel.outbox.OutboxPublisherPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Publishes with publisher confirms: {@link RabbitTemplate#invoke} pins the send and the confirm wait to the same
 * channel, and {@link RabbitTemplate#waitForConfirmsOrDie} throws if the broker never acknowledges the message or
 * is unreachable. That exception propagates to {@code OutboxDispatchWorker}, which leaves the record claimed for
 * retry instead of marking it published -- a broker outage never loses a committed outbox event, it only delays
 * delivery. The outbox event id becomes the AMQP message id, which the consumer uses as its inbox dedup key.
 */
@Component
@ConditionalOnProperty(prefix = "sentinel.messaging", name = "enabled", havingValue = "true")
@Primary
class RabbitMqOutboxPublisher implements OutboxPublisherPort {

	private final RabbitTemplate rabbitTemplate;
	private final MessagingProperties properties;

	RabbitMqOutboxPublisher(RabbitTemplate rabbitTemplate, MessagingProperties properties) {
		this.rabbitTemplate = rabbitTemplate;
		this.properties = properties;
	}

	@Override
	public void publish(OutboxRecord event) {
		rabbitTemplate.invoke(operations -> {
			MessageProperties messageProperties = new MessageProperties();
			messageProperties.setMessageId(event.id().value().toString());
			messageProperties.setContentType("application/json");
			messageProperties.setHeader("aggregateType", event.aggregateType());
			messageProperties.setHeader("aggregateId", event.aggregateId());
			Message message = new Message(event.payload().getBytes(StandardCharsets.UTF_8), messageProperties);

			operations.send(properties.exchange(), event.eventType(), message);
			operations.waitForConfirmsOrDie(properties.publisherConfirmTimeoutMillis());
			return null;
		});
	}
}
