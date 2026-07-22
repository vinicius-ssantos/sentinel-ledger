package io.github.vinicius.sentinel.reconciliation;

import java.util.Objects;
import java.util.UUID;

public record OperatorId(UUID value) {

	public OperatorId {
		Objects.requireNonNull(value, "operator id must not be null");
	}
}
