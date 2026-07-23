package io.github.vinicius.sentinel.integration.messaging.internal;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;

/**
 * Retries happen in-process (stateless: the message stays unacked on the channel while attempts run), so a poison
 * message never leaves the broker until the budget is exhausted. The last attempt's recoverer rejects without
 * requeue, which the queue's dead-letter argument turns into a hand-off to the dead-letter queue instead of an
 * infinite redelivery loop.
 */
@Configuration
@ConditionalOnProperty(prefix = "sentinel.messaging", name = "enabled", havingValue = "true")
class MessagingListenerConfiguration {

	@Bean
	SimpleRabbitListenerContainerFactory messagingListenerContainerFactory(ConnectionFactory connectionFactory, MessagingProperties properties) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAdviceChain(retryInterceptor(properties));
		return factory;
	}

	private MethodInterceptor retryInterceptor(MessagingProperties properties) {
		RetryPolicy retryPolicy = RetryPolicy.builder()
			.maxRetries(properties.maxAttempts())
			.delay(properties.initialInterval())
			.multiplier(properties.multiplier())
			.maxDelay(properties.maxInterval())
			.build();

		return RetryInterceptorBuilder.stateless()
			.retryPolicy(retryPolicy)
			.recoverer(new RejectAndDontRequeueRecoverer())
			.build();
	}
}
