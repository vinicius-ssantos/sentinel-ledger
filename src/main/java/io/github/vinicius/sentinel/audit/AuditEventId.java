package io.github.vinicius.sentinel.audit;

import java.util.Objects;
import java.util.UUID;

public record AuditEventId(UUID value) {

	public AuditEventId {
		Objects.requireNonNull(value, "audit event id must not be null");
	}

	public static AuditEventId generate() {
		return new AuditEventId(UUID.randomUUID());
	}
}
