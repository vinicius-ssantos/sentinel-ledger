package io.github.vinicius.sentinel.webhooks;

/**
 * {@code PENDING} -&gt; {@code DELIVERED} on a 2xx response, or {@code PENDING} -&gt; {@code FAILED} once the caller's
 * retry budget (enforced outside this module, by the messaging consumer's retry/dead-letter policy) is exhausted.
 */
public enum WebhookDeliveryStatus {
	PENDING,
	DELIVERED,
	FAILED
}
