package io.github.vinicius.sentinel.idempotency;

/**
 * Outcome of attempting to acquire an idempotency record for a merchant, operation, and key.
 */
public sealed interface IdempotencyAcquisition {

	/** No record existed yet; the caller now owns it and must complete or fail it. */
	record Acquired() implements IdempotencyAcquisition {}

	/** A terminal outcome already exists for the same canonical request; replay it verbatim. */
	record Replayed(StoredResponse response) implements IdempotencyAcquisition {}

	/** The key is already scoped to a different canonical request. */
	record KeyConflict() implements IdempotencyAcquisition {}

	/** Another request currently owns the processing lease. */
	record InProgress() implements IdempotencyAcquisition {}
}
