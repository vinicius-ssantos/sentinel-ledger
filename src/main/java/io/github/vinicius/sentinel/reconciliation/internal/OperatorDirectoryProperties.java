package io.github.vinicius.sentinel.reconciliation.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "sentinel.operator")
record OperatorDirectoryProperties(List<OperatorCredential> directory) {

	OperatorDirectoryProperties {
		directory = directory == null ? List.of() : List.copyOf(directory);
	}

	record OperatorCredential(UUID id, String apiKeyId, String apiKeySecret) {}
}
