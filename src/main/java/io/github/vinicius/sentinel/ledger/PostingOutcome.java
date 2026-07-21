package io.github.vinicius.sentinel.ledger;

public sealed interface PostingOutcome {

	/** The transaction was newly posted. */
	record Posted(LedgerTransaction transaction) implements PostingOutcome {}

	/** A transaction already exists for this business effect reference; it is returned unchanged, never reposted. */
	record AlreadyPosted(LedgerTransaction existing) implements PostingOutcome {}
}
