package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerPostingIntegrationTests {

	private static final AccountId RECEIVABLE = AccountId.pspClearingReceivable(Currency.BRL);

	@Autowired
	private LedgerPostingPort ledgerPostingPort;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void postsATransactionAndItsEntriesAtomically() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(10_000, Currency.BRL))
			),
			Instant.now()
		);

		PostingOutcome outcome = ledgerPostingPort.post(transaction);

		assertThat(outcome).isInstanceOf(PostingOutcome.Posted.class);
		Integer transactionCount = jdbcTemplate.queryForObject(
			"select count(*) from ledger_transactions where id = ?", Integer.class, transaction.id().value()
		);
		Integer entryCount = jdbcTemplate.queryForObject(
			"select count(*) from ledger_entries where ledger_transaction_id = ?", Integer.class, transaction.id().value()
		);
		assertThat(transactionCount).isEqualTo(1);
		assertThat(entryCount).isEqualTo(2);
	}

	@Test
	void postingTheSameBusinessEffectReferenceTwiceReturnsTheOriginalWithoutDuplicating() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		String reference = "capture:" + UUID.randomUUID();
		LedgerTransaction first = LedgerTransaction.post(
			LedgerTransactionId.generate(), reference, Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(5_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(5_000, Currency.BRL))
			),
			Instant.now()
		);
		LedgerTransaction duplicateAttempt = LedgerTransaction.post(
			LedgerTransactionId.generate(), reference, Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(5_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(5_000, Currency.BRL))
			),
			Instant.now()
		);

		PostingOutcome firstOutcome = ledgerPostingPort.post(first);
		PostingOutcome secondOutcome = ledgerPostingPort.post(duplicateAttempt);

		assertThat(firstOutcome).isInstanceOf(PostingOutcome.Posted.class);
		assertThat(secondOutcome).isInstanceOf(PostingOutcome.AlreadyPosted.class);
		assertThat(((PostingOutcome.AlreadyPosted) secondOutcome).existing().id()).isEqualTo(first.id());

		Integer transactionCount = jdbcTemplate.queryForObject(
			"select count(*) from ledger_transactions where business_effect_reference = ?", Integer.class, reference
		);
		assertThat(transactionCount).isEqualTo(1);
	}

	@Test
	void aCompensatingTransactionReferencesTheOriginalAndLeavesItUnchanged() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		LedgerTransaction capture = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(10_000, Currency.BRL))
			),
			Instant.now()
		);
		ledgerPostingPort.post(capture);

		LedgerTransaction correction = LedgerTransaction.compensate(
			LedgerTransactionId.generate(), "correction:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(payable, EntryDirection.DEBIT, Money.positive(10_000, Currency.BRL)),
				new LedgerEntry(RECEIVABLE, EntryDirection.CREDIT, Money.positive(10_000, Currency.BRL))
			),
			capture.id(),
			Instant.now()
		);

		PostingOutcome outcome = ledgerPostingPort.post(correction);

		assertThat(outcome).isInstanceOf(PostingOutcome.Posted.class);
		UUID reversesId = jdbcTemplate.queryForObject(
			"select reverses_ledger_transaction_id from ledger_transactions where id = ?", UUID.class, correction.id().value()
		);
		assertThat(reversesId).isEqualTo(capture.id().value());

		Integer originalEntryCount = jdbcTemplate.queryForObject(
			"select count(*) from ledger_entries where ledger_transaction_id = ?", Integer.class, capture.id().value()
		);
		assertThat(originalEntryCount).isEqualTo(2);
	}

	@Test
	void postedEntriesCannotBeUpdatedEvenBelowTheApplicationLayer() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(1_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(1_000, Currency.BRL))
			),
			Instant.now()
		);
		ledgerPostingPort.post(transaction);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"update ledger_entries set amount_minor = 999999 where ledger_transaction_id = ?", transaction.id().value()
		)).isInstanceOf(Exception.class);
	}

	@Test
	void postedTransactionsCannotBeDeletedEvenBelowTheApplicationLayer() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(1_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(1_000, Currency.BRL))
			),
			Instant.now()
		);
		ledgerPostingPort.post(transaction);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"delete from ledger_entries where ledger_transaction_id = ?", transaction.id().value()
		)).isInstanceOf(Exception.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
			"delete from ledger_transactions where id = ?", transaction.id().value()
		)).isInstanceOf(Exception.class);
	}

	@Test
	void findsOnlyTransactionsWhoseBusinessEffectReferenceStartsWithThePrefix() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		String paymentIntentId = UUID.randomUUID().toString();
		LedgerTransaction capture = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + paymentIntentId + ":key-1", Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(1_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(1_000, Currency.BRL))
			),
			Instant.now()
		);
		LedgerTransaction unrelated = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + UUID.randomUUID() + ":key-2", Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(1_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(1_000, Currency.BRL))
			),
			Instant.now()
		);
		ledgerPostingPort.post(capture);
		ledgerPostingPort.post(unrelated);

		List<LedgerTransaction> found = ledgerPostingPort.findByBusinessEffectReferencePrefix("capture:" + paymentIntentId + ":");

		assertThat(found).extracting(LedgerTransaction::id).containsExactly(capture.id());
	}

	@Test
	void treatsPercentAndUnderscoreInThePrefixAsLiteralCharactersNotWildcards() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		String weirdKey = "key_with_underscores%and%percents";
		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:pi-1:" + weirdKey, Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(1_000, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(1_000, Currency.BRL))
			),
			Instant.now()
		);
		ledgerPostingPort.post(transaction);

		List<LedgerTransaction> exactPrefixMatch = ledgerPostingPort.findByBusinessEffectReferencePrefix("capture:pi-1:key_with_underscores%and%percents");
		List<LedgerTransaction> wildcardWouldOverMatch = ledgerPostingPort.findByBusinessEffectReferencePrefix("capture:pi-1:keyXwithXunderscores");

		assertThat(exactPrefixMatch).extracting(LedgerTransaction::id).containsExactly(transaction.id());
		assertThat(wildcardWouldOverMatch).isEmpty();
	}

	@Test
	void rejectsAZeroAmountEntryAtTheDatabaseLevelIfSomehowConstructed() {
		assertThatThrownBy(() -> jdbcTemplate.update(
			"insert into ledger_entries (id, ledger_transaction_id, entry_sequence, account_id, direction, amount_minor) "
				+ "values (?, ?, ?, ?, 'DEBIT', 0)",
			UUID.randomUUID(), UUID.randomUUID(), 1, RECEIVABLE.value()
		)).isInstanceOf(DataIntegrityViolationException.class);
	}
}
