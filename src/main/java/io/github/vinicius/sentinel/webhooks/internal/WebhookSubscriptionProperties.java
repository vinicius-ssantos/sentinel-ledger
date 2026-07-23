package io.github.vinicius.sentinel.webhooks.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

/**
 * The single merchant's webhook endpoint (matching the MVP's single-merchant scope). {@code previousSecret} and
 * {@code previousSecretValidUntil} support rotation: this module always signs with {@code secret}, but a receiver
 * example (or a real one) should keep accepting {@code previousSecret} until that instant so an in-flight rotation
 * never causes a receiver to reject a delivery signed a moment before it rolled its own copy over.
 */
@ConfigurationProperties(prefix = "sentinel.webhook")
record WebhookSubscriptionProperties(String url, String secret, String previousSecret, Instant previousSecretValidUntil) {
}
