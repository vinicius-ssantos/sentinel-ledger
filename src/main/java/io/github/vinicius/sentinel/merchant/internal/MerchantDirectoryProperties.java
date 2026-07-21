package io.github.vinicius.sentinel.merchant.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.UUID;

@ConfigurationProperties(prefix = "sentinel.merchant")
record MerchantDirectoryProperties(List<MerchantCredential> directory) {

	record MerchantCredential(UUID id, String apiKeyId, String apiKeySecret) {}
}
