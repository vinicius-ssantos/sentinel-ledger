package io.github.vinicius.sentinel.outbox;

import java.time.Instant;
import java.util.Objects;

/**
 * A publication intent, enqueued by a producing module in the same local transaction as the business change it
 * describes. {@code payload} must be a JSON string built from safe, allowlisted business fields the caller builds
 * explicitly — never a raw request/response body, header, credential, PAN, CVV, or token, the same redaction
 * discipline as {@code audit.AuditEvent}.
 */
public record OutboxEvent(
	OutboxEventId id,
	String aggregateType,
	String aggregateId,
	String eventType,
	String payload,
	Instant createdAt
) {

	public OutboxEvent {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(aggregateType, "aggregateType must not be null");
		Objects.requireNonNull(aggregateId, "aggregateId must not be null");
		Objects.requireNonNull(eventType, "eventType must not be null");
		Objects.requireNonNull(payload, "payload must not be null");
		Objects.requireNonNull(createdAt, "createdAt must not be null");
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

	public static OutboxEvent enqueue(String aggregateType, String aggregateId, String eventType, String payload, Instant createdAt) {
		return new OutboxEvent(OutboxEventId.generate(), aggregateType, aggregateId, eventType, payload, createdAt);
	}
}
