package io.github.vinicius.sentinel.integration.messaging.internal;

import io.github.vinicius.sentinel.integration.messaging.MessagingQueryPort;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "sentinel.messaging", name = "enabled", havingValue = "true")
@Primary
class RabbitMqMessagingQueryGateway implements MessagingQueryPort {

	private final AmqpAdmin amqpAdmin;
	private final MessagingProperties properties;

	RabbitMqMessagingQueryGateway(AmqpAdmin amqpAdmin, MessagingProperties properties) {
		this.amqpAdmin = amqpAdmin;
		this.properties = properties;
	}

	@Override
	public long deadLetterQueueDepth() {
		QueueInformation info = amqpAdmin.getQueueInfo(properties.deadLetterQueue());
		return info == null ? 0 : info.getMessageCount();
	}
}
