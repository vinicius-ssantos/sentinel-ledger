package io.github.vinicius.sentinel.outbox.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "sentinel.outbox")
record OutboxProperties(
	int batchSize,
	Duration dispatchInterval,
	Duration claimStaleAfter,
	Duration reclaimInterval,
	int maxAttempts
) {

	OutboxProperties {
		if (batchSize <= 0) {
			batchSize = 50;
		}
		if (dispatchInterval == null) {
			dispatchInterval = Duration.ofSeconds(5);
		}
		if (claimStaleAfter == null) {
			claimStaleAfter = Duration.ofMinutes(2);
		}
		if (reclaimInterval == null) {
			reclaimInterval = Duration.ofMinutes(1);
		}
		if (maxAttempts <= 0) {
			maxAttempts = 5;
		}
	}
}
