package io.github.vinicius.sentinel.reconciliation.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "sentinel.reconciliation")
record ReconciliationSweepProperties(Duration staleAfter) {

	ReconciliationSweepProperties {
		if (staleAfter == null) {
			staleAfter = Duration.ofMinutes(15);
		}
	}
}
