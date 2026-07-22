package io.github.vinicius.sentinel.reconciliation.internal.web;

import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.reconciliation.CurrentOperatorResolver;
import io.github.vinicius.sentinel.reconciliation.OperatorId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCase;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCasePort;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseStatus;
import io.github.vinicius.sentinel.reconciliation.internal.PaymentIntentReconciliationCheckService;
import io.github.vinicius.sentinel.reconciliation.internal.ReconciliationCheckOutcome;
import io.github.vinicius.sentinel.reconciliation.internal.ReconciliationResolutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Privileged operator API: restricted to ROLE_OPERATOR by SecurityConfig, distinct from the merchant-facing
 * payments API. Lives in {@code reconciliation}, unlike the ledger-entries controller in #20 — reconciliation
 * needs no {@code merchant} module access at all, since it owns its own operator identity.
 */
@RestController
@RequestMapping("/api/v1/reconciliation")
@SecurityRequirement(name = "operatorBasicAuth")
class ReconciliationController {

	private final ReconciliationCasePort reconciliationCasePort;
	private final ReconciliationResolutionService reconciliationResolutionService;
	private final PaymentIntentReconciliationCheckService checkService;
	private final CurrentOperatorResolver currentOperatorResolver;

	ReconciliationController(
		ReconciliationCasePort reconciliationCasePort,
		ReconciliationResolutionService reconciliationResolutionService,
		PaymentIntentReconciliationCheckService checkService,
		CurrentOperatorResolver currentOperatorResolver
	) {
		this.reconciliationCasePort = reconciliationCasePort;
		this.reconciliationResolutionService = reconciliationResolutionService;
		this.checkService = checkService;
		this.currentOperatorResolver = currentOperatorResolver;
	}

	@GetMapping("/cases")
	@Operation(
		summary = "List detected mismatches",
		description = "Newest first. Filter by status to see open-case counts, ages, and severities at a glance."
	)
	@ApiResponse(responseCode = "200", description = "Matching cases")
	List<ReconciliationCaseResponse> cases(@RequestParam(required = false) ReconciliationCaseStatus status) {
		return reconciliationCasePort.findAll(status).stream().map(ReconciliationCaseResponse::from).toList();
	}

	@PostMapping("/cases/{id}/resolve")
	@Operation(
		summary = "Record an operator resolution",
		description = "Requires an authenticated operator, a non-blank reason, and explicit confirmation. "
			+ "COMPENSATE posts a new compensating ledger transaction; the original evidence is never edited."
	)
	@ApiResponse(responseCode = "200", description = "Case resolved")
	@ApiResponse(responseCode = "400", description = "Missing reason or confirmation")
	@ApiResponse(responseCode = "404", description = "No such case")
	@ApiResponse(responseCode = "409", description = "Case is already resolved or ignored")
	ReconciliationCaseResponse resolve(@PathVariable UUID id, @Valid @RequestBody ResolveReconciliationCaseRequest request) {
		OperatorId operatorId = currentOperatorResolver.requireCurrentOperatorId();
		ReconciliationCaseId caseId = new ReconciliationCaseId(id);
		try {
			ReconciliationCase resolved = reconciliationResolutionService.resolve(caseId, operatorId, request.reason(), request.action());
			return ReconciliationCaseResponse.from(resolved);
		} catch (IllegalArgumentException e) {
			throw new ReconciliationCaseNotFoundException();
		} catch (IllegalStateException e) {
			throw new ReconciliationCaseAlreadyResolvedException(e.getMessage());
		}
	}

	@PostMapping("/checks/payment-intents/{id}")
	@Operation(
		summary = "Run an on-demand reconciliation check for one payment intent",
		description = "Compares local state against a fresh provider status lookup. Safely auto-resolves an "
			+ "uncertain local state; opens (or returns the existing) case for a genuine divergence."
	)
	@ApiResponse(responseCode = "200", description = "Check completed")
	@ApiResponse(responseCode = "404", description = "No such payment intent")
	ReconciliationCheckResponse check(@PathVariable UUID id) {
		try {
			ReconciliationCheckOutcome outcome = checkService.check(new PaymentIntentId(id));
			return ReconciliationCheckResponse.from(outcome);
		} catch (IllegalArgumentException e) {
			throw new PaymentIntentNotFoundException();
		}
	}
}
