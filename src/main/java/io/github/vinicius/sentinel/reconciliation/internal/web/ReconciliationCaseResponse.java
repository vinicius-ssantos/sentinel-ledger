package io.github.vinicius.sentinel.reconciliation.internal.web;

import io.github.vinicius.sentinel.reconciliation.ReconciliationCase;
import io.github.vinicius.sentinel.reconciliation.ReconciliationResolution;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record ReconciliationCaseResponse(

	UUID id,
	UUID paymentIntentId,

	@Schema(example = "AUTHORIZATION_OUTCOME_DIVERGENCE")
	String mismatchType,

	@Schema(example = "HIGH")
	String severity,

	@Schema(example = "OPEN")
	String status,

	@Schema(example = "CAPTURED")
	String localEvidence,

	@Schema(example = "DECLINED:insufficient_funds")
	String providerEvidence,

	Instant detectedAt,

	ReconciliationResolutionResponse resolution
) {

	public static ReconciliationCaseResponse from(ReconciliationCase reconciliationCase) {
		return new ReconciliationCaseResponse(
			reconciliationCase.id().value(),
			reconciliationCase.paymentIntentId().value(),
			reconciliationCase.mismatchType().name(),
			reconciliationCase.severity().name(),
			reconciliationCase.status().name(),
			reconciliationCase.localEvidence(),
			reconciliationCase.providerEvidence(),
			reconciliationCase.detectedAt(),
			reconciliationCase.resolutionIfPresent().map(ReconciliationCaseResponse::toResolutionResponse).orElse(null)
		);
	}

	private static ReconciliationResolutionResponse toResolutionResponse(ReconciliationResolution resolution) {
		return new ReconciliationResolutionResponse(
			resolution.actor().value(),
			resolution.reason(),
			resolution.action().name(),
			resolution.compensatingTransactionReference(),
			resolution.resolvedAt()
		);
	}

	public record ReconciliationResolutionResponse(
		UUID operatorId,
		String reason,
		@Schema(example = "COMPENSATE") String action,
		String compensatingTransactionReference,
		Instant resolvedAt
	) {}
}
