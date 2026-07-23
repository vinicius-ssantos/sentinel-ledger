package io.github.vinicius.sentinel.integration.messaging.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code enabled} gates every bean in this module (topology, publisher, listener, container factory): when false
 * (the default), nothing in this module connects to or declares anything on a broker, so RabbitMQ is never required
 * to run the rest of the test suite.
 */
@ConfigurationProperties(prefix = "sentinel.messaging")
record MessagingProperties(
	boolean enabled,
	String exchange,
	String queue,
	String deadLetterExchange,
	String deadLetterQueue,
	int maxAttempts,
	Duration initialInterval,
	double multiplier,
	Duration maxInterval,
	long publisherConfirmTimeoutMillis
) {

	MessagingProperties {
		if (exchange == null || exchange.isBlank()) {
			exchange = "sentinel.events";
		}
		if (queue == null || queue.isBlank()) {
			queue = "sentinel.events.q";
		}
		if (deadLetterExchange == null || deadLetterExchange.isBlank()) {
			deadLetterExchange = "sentinel.events.dlx";
		}
		if (deadLetterQueue == null || deadLetterQueue.isBlank()) {
			deadLetterQueue = "sentinel.events.dlq";
		}
		if (maxAttempts <= 0) {
			maxAttempts = 5;
		}
		if (initialInterval == null) {
			initialInterval = Duration.ofMillis(500);
		}
		if (multiplier <= 0) {
			multiplier = 2.0;
		}
		if (maxInterval == null) {
			maxInterval = Duration.ofSeconds(10);
		}
		if (publisherConfirmTimeoutMillis <= 0) {
			publisherConfirmTimeoutMillis = 5000;
		}
	}
}
