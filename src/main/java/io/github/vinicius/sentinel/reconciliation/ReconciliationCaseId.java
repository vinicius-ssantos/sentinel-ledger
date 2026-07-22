package io.github.vinicius.sentinel.reconciliation;

import java.util.Objects;
import java.util.UUID;

public record ReconciliationCaseId(UUID value) {

	public ReconciliationCaseId {
		Objects.requireNonNull(value, "reconciliation case id must not be null");
	}

	public static ReconciliationCaseId generate() {
		return new ReconciliationCaseId(UUID.randomUUID());
	}
}
