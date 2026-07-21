package io.github.vinicius.sentinel.integration.psp;

import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.payments.PspCallback;
import io.github.vinicius.sentinel.payments.PspProviderReference;

/**
 * Deterministic test/demo-only control surface for the simulated PSP. Never referenced by the merchant-facing API:
 * only test code and demo seed data may program a scenario. An attempt that is never programmed here authorizes
 * with a default approval, so ordinary flows work without touching this type at all.
 */
public interface SimulatedPspControls {

	void programApproval(PspAttemptId attemptId, PspProviderReference reference);

	void programDecline(PspAttemptId attemptId, String reasonCode);

	/** The provider never processes the request; both {@code authorize} and {@code checkStatus} report {@code Unknown}. */
	void programTimeoutBeforeProcessing(PspAttemptId attemptId);

	/** The provider processes the request but the synchronous response is lost; {@code checkStatus} recovers {@code trueOutcome}. */
	void programTimeoutAfterProcessing(PspAttemptId attemptId, PspAuthorizationResult trueOutcome);

	void programRetryableFailure(PspAttemptId attemptId, String detail);

	void programPermanentFailure(PspAttemptId attemptId, String detail);

	/** {@code authorize} and the first callback report {@code reportedOutcome}; {@code checkStatus} reveals a diverging {@code trueOutcome}. */
	void programStatusMismatch(PspAttemptId attemptId, PspAuthorizationResult reportedOutcome, PspAuthorizationResult trueOutcome);

	/** Delivers the latest callback for the attempt. */
	PspCallback deliverCallback(PspAttemptId attemptId);

	/** Redelivers a specific historical sequence, letting tests simulate out-of-order or duplicate delivery. */
	PspCallback deliverCallback(PspAttemptId attemptId, long sequence);

	/** Clears every programmed attempt and callback history. */
	void reset();
}
