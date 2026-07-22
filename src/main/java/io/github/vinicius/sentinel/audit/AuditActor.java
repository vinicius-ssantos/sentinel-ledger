package io.github.vinicius.sentinel.audit;

import java.util.Objects;

public record AuditActor(AuditActorType type, String id) {

	public AuditActor {
		Objects.requireNonNull(type, "actor type must not be null");
		Objects.requireNonNull(id, "actor id must not be null");
		if (id.isBlank()) {
			throw new IllegalArgumentException("actor id must not be blank");
		}
	}

	public static AuditActor merchant(String merchantId) {
		return new AuditActor(AuditActorType.MERCHANT, merchantId);
	}
}
