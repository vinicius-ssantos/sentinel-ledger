package io.github.vinicius.sentinel.reconciliation.internal.web;

import io.github.vinicius.sentinel.reconciliation.ReconciliationCase;
import io.github.vinicius.sentinel.reconciliation.ReconciliationOpenOutcome;
import io.github.vinicius.sentinel.reconciliation.internal.ReconciliationCheckOutcome;
import io.swagger.v3.oas.annotations.media.Schema;

record ReconciliationCheckResponse(

	@Schema(example = "MISMATCH_DETECTED")
	String outcome,

	@Schema(example = "AUTHORIZED", nullable = true)
	String resolvedState,

	ReconciliationCaseResponse reconciliationCase
) {

	static ReconciliationCheckResponse from(ReconciliationCheckOutcome outcome) {
		if (outcome instanceof ReconciliationCheckOutcome.NoEvidence) {
			return new ReconciliationCheckResponse("NO_EVIDENCE", null, null);
		}
		if (outcome instanceof ReconciliationCheckOutcome.AutoResolved autoResolved) {
			return new ReconciliationCheckResponse("AUTO_RESOLVED", autoResolved.resolvedState().name(), null);
		}
		if (outcome instanceof ReconciliationCheckOutcome.AlreadyConsistent consistent) {
			return new ReconciliationCheckResponse("ALREADY_CONSISTENT", consistent.state().name(), null);
		}
		if (outcome instanceof ReconciliationCheckOutcome.MismatchDetected mismatch) {
			ReconciliationCase reconciliationCase = extractCase(mismatch.outcome());
			return new ReconciliationCheckResponse("MISMATCH_DETECTED", null, ReconciliationCaseResponse.from(reconciliationCase));
		}
		throw new IllegalArgumentException("unsupported reconciliation check outcome: " + outcome);
	}

	private static ReconciliationCase extractCase(ReconciliationOpenOutcome outcome) {
		if (outcome instanceof ReconciliationOpenOutcome.Opened opened) {
			return opened.opened();
		}
		return ((ReconciliationOpenOutcome.AlreadyOpen) outcome).existing();
	}
}
