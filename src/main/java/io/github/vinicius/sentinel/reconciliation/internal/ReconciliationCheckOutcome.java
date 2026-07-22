package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.payments.PaymentIntentState;
import io.github.vinicius.sentinel.reconciliation.ReconciliationOpenOutcome;

public sealed interface ReconciliationCheckOutcome {

	/** The payment intent never began authorization; there is nothing to reconcile against. */
	record NoEvidence() implements ReconciliationCheckOutcome {}

	/** Local state was still uncertain; fresh provider evidence safely resolved it without opening a case. */
	record AutoResolved(PaymentIntentState resolvedState) implements ReconciliationCheckOutcome {}

	/** Local state already matches the provider's evidence; nothing to do. */
	record AlreadyConsistent(PaymentIntentState state) implements ReconciliationCheckOutcome {}

	/** Local state and provider evidence genuinely diverge; see the wrapped case (new or already open). */
	record MismatchDetected(ReconciliationOpenOutcome outcome) implements ReconciliationCheckOutcome {}
}
