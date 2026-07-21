package io.github.vinicius.sentinel.ledger;

import java.util.Objects;
import java.util.UUID;

public record LedgerTransactionId(UUID value) {

	public LedgerTransactionId {
		Objects.requireNonNull(value, "ledger transaction id must not be null");
	}

	public static LedgerTransactionId generate() {
		return new LedgerTransactionId(UUID.randomUUID());
	}
}
