package io.github.vinicius.sentinel.outbox;

import java.util.Objects;
import java.util.UUID;

public record OutboxEventId(UUID value) {

	public OutboxEventId {
		Objects.requireNonNull(value, "outbox event id must not be null");
	}

	public static OutboxEventId generate() {
		return new OutboxEventId(UUID.randomUUID());
	}
}
