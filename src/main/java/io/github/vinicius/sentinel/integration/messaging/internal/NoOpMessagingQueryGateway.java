package io.github.vinicius.sentinel.integration.messaging.internal;

import io.github.vinicius.sentinel.integration.messaging.MessagingQueryPort;
import org.springframework.stereotype.Component;

/**
 * Default when {@code sentinel.messaging.enabled} is false: there is no broker to ask, so there can be no
 * dead-lettered messages to report.
 */
@Component
class NoOpMessagingQueryGateway implements MessagingQueryPort {

	@Override
	public long deadLetterQueueDepth() {
		return 0;
	}
}
