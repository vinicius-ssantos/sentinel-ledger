package io.github.vinicius.sentinel.reconciliation;

/** Mirrors the ledger module's {@code PostingOutcome} dedup pattern: opening never creates a duplicate open case. */
public sealed interface ReconciliationOpenOutcome {

	record Opened(ReconciliationCase opened) implements ReconciliationOpenOutcome {}

	/** An open (or investigating) case already exists for this exact fingerprint; no new case was created. */
	record AlreadyOpen(ReconciliationCase existing) implements ReconciliationOpenOutcome {}
}
