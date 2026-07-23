package io.github.vinicius.sentinel.webhooks;

import java.time.Instant;

public record WebhookDeliveryRecord(
	WebhookDeliveryId id,
	String aggregateType,
	String aggregateId,
	String eventType,
	WebhookDeliveryStatus status,
	int attemptCount,
	String lastError,
	Instant createdAt,
	Instant deliveredAt
) {
}
