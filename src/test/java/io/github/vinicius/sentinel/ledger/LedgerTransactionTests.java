package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTests {

	private static final AccountId RECEIVABLE = AccountId.pspClearingReceivable(Currency.BRL);
	private static final AccountId PAYABLE = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);

	@Test
	void postsATwoEntryBalancedTransaction() {
		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:pay_001", Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(PAYABLE, EntryDirection.CREDIT, Money.positive(10_000, Currency.BRL))
			),
			Instant.now()
		);

		assertThat(transaction.entries()).hasSize(2);
		assertThat(transaction.isCompensating()).isFalse();
	}

	@Test
	void rejectsFewerThanTwoEntries() {
		assertThatThrownBy(() -> LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:pay_001", Currency.BRL,
			List.of(new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL))),
			Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAnUnbalancedTransaction() {
		assertThatThrownBy(() -> LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:pay_001", Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(PAYABLE, EntryDirection.CREDIT, Money.positive(9_000, Currency.BRL))
			),
			Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMixedCurrencyEntries() {
		Currency usd = new Currency("USD", 2);
		assertThatThrownBy(() -> LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:pay_001", Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(PAYABLE, EntryDirection.CREDIT, Money.positive(10_000, usd))
			),
			Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANonPositiveEntryAmount() {
		assertThatThrownBy(() -> new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.zero(Currency.BRL)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void compensatingTransactionMustReferenceTheOriginal() {
		assertThatThrownBy(() -> LedgerTransaction.compensate(
			LedgerTransactionId.generate(), "correction:pay_001", Currency.BRL,
			List.of(
				new LedgerEntry(PAYABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(RECEIVABLE, EntryDirection.CREDIT, Money.positive(10_000, Currency.BRL))
			),
			null,
			Instant.now()
		)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void aValidCompensatingTransactionIsMarkedAsSuch() {
		LedgerTransactionId original = LedgerTransactionId.generate();
		LedgerTransaction compensation = LedgerTransaction.compensate(
			LedgerTransactionId.generate(), "correction:pay_001", Currency.BRL,
			List.of(
				new LedgerEntry(PAYABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(RECEIVABLE, EntryDirection.CREDIT, Money.positive(10_000, Currency.BRL))
			),
			original,
			Instant.now()
		);

		assertThat(compensation.isCompensating()).isTrue();
		assertThat(compensation.reversesLedgerTransactionId()).isEqualTo(original);
	}

	@RepeatedTest(50)
	void randomlyGeneratedBalancedEntrySetsAlwaysPost() {
		Random random = new Random();
		int debitEntryCount = 1 + random.nextInt(4);

		List<LedgerEntry> entries = new ArrayList<>();
		long total = 0;
		for (int i = 0; i < debitEntryCount; i++) {
			long share = 1 + random.nextInt(100_000);
			total += share;
			entries.add(new LedgerEntry(
				AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL), EntryDirection.DEBIT, Money.positive(share, Currency.BRL)
			));
		}
		entries.add(new LedgerEntry(RECEIVABLE, EntryDirection.CREDIT, Money.positive(total, Currency.BRL)));

		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(), "random:" + UUID.randomUUID(), Currency.BRL, entries, Instant.now()
		);

		assertThat(transaction.entries()).hasSameSizeAs(entries);
	}

	@RepeatedTest(50)
	void randomlyGeneratedUnbalancedEntrySetsAlwaysFail() {
		Random random = new Random();
		long debit = 1 + random.nextInt(1_000_000);
		long credit = debit + 1 + random.nextInt(1_000);

		assertThatThrownBy(() -> LedgerTransaction.post(
			LedgerTransactionId.generate(), "random:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(debit, Currency.BRL)),
				new LedgerEntry(PAYABLE, EntryDirection.CREDIT, Money.positive(credit, Currency.BRL))
			),
			Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
