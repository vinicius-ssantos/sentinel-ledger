package io.github.vinicius.sentinel.integration.messaging.internal;

import io.github.vinicius.sentinel.integration.messaging.MessagingQueryPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Registered unconditionally: {@link MessagingQueryPort} always has a bean (a no-op reporting {@code 0} when
 * messaging is disabled), so this gauge is safe to expose regardless of {@code sentinel.messaging.enabled}.
 */
@Component
class DeadLetterQueueDepthGauge {

	DeadLetterQueueDepthGauge(MessagingQueryPort messagingQueryPort, MeterRegistry meterRegistry) {
		Gauge.builder("sentinel.messaging.dead_letter_queue.depth", messagingQueryPort, MessagingQueryPort::deadLetterQueueDepth)
			.description("Messages currently sitting in the dead-letter queue")
			.register(meterRegistry);
	}
}
