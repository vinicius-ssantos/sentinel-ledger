package io.github.vinicius.sentinel.integration.messaging.internal;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A topic exchange carries every outbox event, routed by {@code eventType}; the dispatch queue binds {@code #} so
 * it receives all of them. Each message that exhausts its retry budget is rejected without requeue, which the
 * queue's {@code x-dead-letter-exchange} argument routes to a fanout dead-letter exchange and its queue — no
 * routing key needs to survive the hand-off since there is exactly one dead-letter destination today.
 */
@Configuration
@ConditionalOnProperty(prefix = "sentinel.messaging", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessagingProperties.class)
class MessagingTopology {

	@Bean
	Declarables messagingDeclarables(MessagingProperties properties) {
		TopicExchange exchange = new TopicExchange(properties.exchange(), true, false);
		FanoutExchange deadLetterExchange = new FanoutExchange(properties.deadLetterExchange(), true, false);

		Queue queue = QueueBuilder.durable(properties.queue())
			.deadLetterExchange(properties.deadLetterExchange())
			.build();
		Queue deadLetterQueue = QueueBuilder.durable(properties.deadLetterQueue()).build();

		Binding queueBinding = BindingBuilder.bind(queue).to(exchange).with("#");
		Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange);

		return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue, queueBinding, deadLetterBinding);
	}
}
