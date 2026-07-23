package io.github.vinicius.sentinel.outbox;

import java.time.Instant;

/**
 * The persisted read model of one outbox event, including its current lifecycle state — used for operational
 * visibility into stuck and failed publications. {@link #claimedAt()} and {@link #publishedAt()} are {@code null}
 * while the record is still {@link OutboxEventStatus#PENDING}.
 */
public record OutboxRecord(
	OutboxEventId id,
	String aggregateType,
	String aggregateId,
	String eventType,
	String payload,
	OutboxEventStatus status,
	int attemptCount,
	String lastError,
	Instant createdAt,
	Instant claimedAt,
	Instant publishedAt
) {
}
