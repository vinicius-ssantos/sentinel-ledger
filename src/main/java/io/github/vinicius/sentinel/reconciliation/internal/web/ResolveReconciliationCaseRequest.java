package io.github.vinicius.sentinel.reconciliation.internal.web;

import io.github.vinicius.sentinel.reconciliation.ReconciliationResolutionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

record ResolveReconciliationCaseRequest(

	@NotBlank
	@Schema(description = "Why this resolution is correct. Required and preserved as permanent evidence.", example = "Confirmed with provider support ticket #4821; funds were never actually captured.")
	String reason,

	@NotNull
	@Schema(example = "COMPENSATE")
	ReconciliationResolutionAction action,

	@AssertTrue(message = "resolution must be explicitly confirmed")
	@Schema(description = "Must be true. An explicit confirmation guards against an accidental financial correction.")
	boolean confirm
) {}
