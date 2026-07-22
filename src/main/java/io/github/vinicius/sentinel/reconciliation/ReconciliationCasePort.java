package io.github.vinicius.sentinel.reconciliation;

import java.util.List;
import java.util.Optional;

public interface ReconciliationCasePort {

	/**
	 * Opens a new case, or returns the already-open case for the same {@link ReconciliationCase#fingerprint()} if
	 * one exists with status {@code OPEN} or {@code INVESTIGATING} — REC-001. A fingerprint that was previously
	 * resolved or ignored opens a fresh case: closed evidence never silently swallows a new occurrence.
	 */
	ReconciliationOpenOutcome open(ReconciliationCase candidate);

	/** Appends a resolution to an open case. The case's original evidence, fingerprint, and detection time are untouched. */
	ReconciliationCase resolve(ReconciliationCaseId id, ReconciliationResolution resolution);

	Optional<ReconciliationCase> findById(ReconciliationCaseId id);

	/** All cases, optionally filtered by status, newest first. */
	List<ReconciliationCase> findAll(ReconciliationCaseStatus statusFilter);
}
