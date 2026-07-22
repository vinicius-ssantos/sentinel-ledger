package io.github.vinicius.sentinel.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Evidence of one sensitive business or operator command. {@code metadata} must contain only safe, allowlisted
 * business fields the caller builds explicitly (state names, amounts, identifiers) — never a raw request/response
 * body, header, credential, PAN, CVV, or token. There is no runtime scanner for this; callers are the redaction
 * boundary, the same posture as the rest of the codebase's logging and fixtures.
 */
public record AuditEvent(
	AuditEventId id,
	AuditActor actor,
	String action,
	String resourceType,
	String resourceId,
	String correlationId,
	String reason,
	Map<String, String> metadata,
	Instant occurredAt
) {

	public AuditEvent {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(actor, "actor must not be null");
		Objects.requireNonNull(action, "action must not be null");
		Objects.requireNonNull(resourceType, "resourceType must not be null");
		Objects.requireNonNull(resourceId, "resourceId must not be null");
		Objects.requireNonNull(correlationId, "correlationId must not be null");
		Objects.requireNonNull(metadata, "metadata must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		if (action.isBlank()) {
			throw new IllegalArgumentException("action must not be blank");
		}
		if (resourceType.isBlank()) {
			throw new IllegalArgumentException("resourceType must not be blank");
		}
		if (resourceId.isBlank()) {
			throw new IllegalArgumentException("resourceId must not be blank");
		}
		if (correlationId.isBlank()) {
			throw new IllegalArgumentException("correlationId must not be blank");
		}
		metadata = Map.copyOf(metadata);
	}

	public static AuditEvent record(
		AuditActor actor, String action, String resourceType, String resourceId,
		String correlationId, Map<String, String> metadata, Instant occurredAt
	) {
		return new AuditEvent(
			AuditEventId.generate(), actor, action, resourceType, resourceId, correlationId, null, metadata, occurredAt
		);
	}
}
