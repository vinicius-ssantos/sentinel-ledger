package io.github.vinicius.sentinel.webhooks;

import java.util.Objects;

public record WebhookDeliveryRequest(
	WebhookDeliveryId id,
	String aggregateType,
	String aggregateId,
	String eventType,
	String payload
) {

	public WebhookDeliveryRequest {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(aggregateType, "aggregateType must not be null");
		Objects.requireNonNull(aggregateId, "aggregateId must not be null");
		Objects.requireNonNull(eventType, "eventType must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		if (aggregateType.isBlank()) {
			throw new IllegalArgumentException("aggregateType must not be blank");
		}
		if (aggregateId.isBlank()) {
			throw new IllegalArgumentException("aggregateId must not be blank");
		}
		if (eventType.isBlank()) {
			throw new IllegalArgumentException("eventType must not be blank");
		}
		if (payload.isBlank()) {
			throw new IllegalArgumentException("payload must not be blank");
		}
	}
}
