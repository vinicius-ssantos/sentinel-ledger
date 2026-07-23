package io.github.vinicius.sentinel.integration.messaging;

public interface MessagingQueryPort {

	/**
	 * Number of messages currently sitting in the dead-letter queue -- retry-exhausted, poison messages that need
	 * operator attention. {@code 0} both when the queue is empty and when messaging is disabled.
	 */
	long deadLetterQueueDepth();
}
