package io.github.vinicius.sentinel.payments.internal;

import java.time.Instant;
import java.util.Map;

/**
 * One correlated fact in a payment intent's timeline. {@code details} follows the same allowlisted-safe-metadata
 * contract as {@code io.github.vinicius.sentinel.audit.AuditEvent}: only business fields callers build explicitly,
 * never a raw request/response body or secret.
 */
public record TimelineEntry(TimelineEntryType type, String label, Instant occurredAt, Map<String, String> details) {

	public TimelineEntry {
		details = Map.copyOf(details);
	}
}
