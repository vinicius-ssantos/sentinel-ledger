package io.github.vinicius.sentinel.reconciliation;

import java.time.Instant;
import java.util.Objects;

public record ReconciliationResolution(
	OperatorId actor,
	String reason,
	ReconciliationResolutionAction action,
	String compensatingTransactionReference,
	Instant resolvedAt
) {

	public ReconciliationResolution {
		Objects.requireNonNull(actor, "actor must not be null");
		Objects.requireNonNull(reason, "reason must not be null");
		if (reason.isBlank()) {
			throw new IllegalArgumentException("reason must not be blank");
		}
		Objects.requireNonNull(action, "action must not be null");
		if (action == ReconciliationResolutionAction.COMPENSATE) {
			Objects.requireNonNull(compensatingTransactionReference, "COMPENSATE requires a compensating transaction reference");
		}
		Objects.requireNonNull(resolvedAt, "resolvedAt must not be null");
	}
}
