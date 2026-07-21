package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An immutable, balanced group of two or more same-currency entries. Once constructed, a transaction is valid by
 * definition: there is no application code path that can produce an unbalanced or mixed-currency instance.
 */
public record LedgerTransaction(
	LedgerTransactionId id,
	String businessEffectReference,
	Currency currency,
	List<LedgerEntry> entries,
	LedgerTransactionId reversesLedgerTransactionId,
	Instant postedAt
) {

	public LedgerTransaction {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(businessEffectReference, "businessEffectReference must not be null");
		if (businessEffectReference.isBlank()) {
			throw new IllegalArgumentException("businessEffectReference must not be blank");
		}
		Objects.requireNonNull(currency, "currency must not be null");
		Objects.requireNonNull(entries, "entries must not be null");
		entries = List.copyOf(entries);
		if (entries.size() < 2) {
			throw new IllegalArgumentException("a ledger transaction requires at least two entries");
		}
		for (LedgerEntry entry : entries) {
			if (!entry.amount().currency().equals(currency)) {
				throw new IllegalArgumentException("all entries must use the transaction currency " + currency.code());
			}
		}
		Money debitTotal = sum(entries, EntryDirection.DEBIT, currency);
		Money creditTotal = sum(entries, EntryDirection.CREDIT, currency);
		if (!debitTotal.equals(creditTotal)) {
			throw new IllegalArgumentException(
				"unbalanced ledger transaction: debits=" + debitTotal.amountInMinorUnitsText()
					+ " credits=" + creditTotal.amountInMinorUnitsText()
			);
		}
		Objects.requireNonNull(postedAt, "postedAt must not be null");
	}

	public static LedgerTransaction post(
		LedgerTransactionId id, String businessEffectReference, Currency currency, List<LedgerEntry> entries, Instant postedAt
	) {
		return new LedgerTransaction(id, businessEffectReference, currency, entries, null, postedAt);
	}

	public static LedgerTransaction compensate(
		LedgerTransactionId id,
		String businessEffectReference,
		Currency currency,
		List<LedgerEntry> entries,
		LedgerTransactionId reversesLedgerTransactionId,
		Instant postedAt
	) {
		Objects.requireNonNull(reversesLedgerTransactionId, "a compensating transaction must reference the transaction it reverses");
		return new LedgerTransaction(id, businessEffectReference, currency, entries, reversesLedgerTransactionId, postedAt);
	}

	public boolean isCompensating() {
		return reversesLedgerTransactionId != null;
	}

	private static Money sum(List<LedgerEntry> entries, EntryDirection direction, Currency currency) {
		Money total = Money.zero(currency);
		for (LedgerEntry entry : entries) {
			if (entry.direction() == direction) {
				total = total.add(entry.amount());
			}
		}
		return total;
	}
}
