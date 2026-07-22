package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerEntryQueryIntegrationTests {

	private static final AccountId RECEIVABLE = AccountId.pspClearingReceivable(Currency.BRL);

	@Autowired
	private LedgerPostingPort ledgerPostingPort;

	@Autowired
	private LedgerEntryQueryPort ledgerEntryQueryPort;

	@Test
	void pagesThroughEveryEntryForAnAccountWithoutSkippingOrDuplicatingAcrossPages() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		List<LedgerTransaction> posted = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			LedgerTransaction transaction = LedgerTransaction.post(
				LedgerTransactionId.generate(), "capture:" + UUID.randomUUID(), Currency.BRL,
				List.of(
					new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(100, Currency.BRL)),
					new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(100, Currency.BRL))
				),
				Instant.now()
			);
			ledgerPostingPort.post(transaction);
			posted.add(transaction);
		}

		List<String> seenReferences = new ArrayList<>();
		String cursor = null;
		int pageCount = 0;
		do {
			LedgerEntryPage page = ledgerEntryQueryPort.findByAccount(payable, cursor, 3);
			page.entries().forEach(entry -> seenReferences.add(entry.businessEffectReference()));
			cursor = page.nextCursor();
			pageCount++;
			assertThat(pageCount).isLessThanOrEqualTo(10);
		} while (cursor != null);

		assertThat(seenReferences).hasSize(7);
		assertThat(seenReferences).doesNotHaveDuplicates();
		assertThat(seenReferences).containsExactlyElementsOf(
			posted.stream().map(LedgerTransaction::businessEffectReference).toList()
		);
	}

	@Test
	void returnsANullCursorOnTheLastPage() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		LedgerTransaction transaction = LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(RECEIVABLE, EntryDirection.DEBIT, Money.positive(500, Currency.BRL)),
				new LedgerEntry(payable, EntryDirection.CREDIT, Money.positive(500, Currency.BRL))
			),
			Instant.now()
		);
		ledgerPostingPort.post(transaction);

		LedgerEntryPage page = ledgerEntryQueryPort.findByAccount(payable, null, 50);

		assertThat(page.entries()).hasSize(1);
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void returnsAnEmptyPageForAnAccountWithNoEntries() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);

		LedgerEntryPage page = ledgerEntryQueryPort.findByAccount(payable, null, 50);

		assertThat(page.entries()).isEmpty();
		assertThat(page.nextCursor()).isNull();
	}

	@Test
	void rejectsAGarbageCursor() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);

		assertThatThrownBy(() -> ledgerEntryQueryPort.findByAccount(payable, "not-a-real-cursor", 10))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
