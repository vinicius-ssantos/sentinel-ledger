package io.github.vinicius.sentinel.ledger;

import java.util.List;
import java.util.Objects;

/**
 * One page of {@link PostedLedgerEntry} results. {@code nextCursor} is {@code null} exactly when {@code entries}
 * is the final page; pass it back to {@link LedgerEntryQueryPort#findByAccount} to resume immediately after the
 * last entry returned, without skipping or repeating entries appended concurrently.
 */
public record LedgerEntryPage(List<PostedLedgerEntry> entries, String nextCursor) {

	public LedgerEntryPage {
		Objects.requireNonNull(entries, "entries must not be null");
		entries = List.copyOf(entries);
	}
}
