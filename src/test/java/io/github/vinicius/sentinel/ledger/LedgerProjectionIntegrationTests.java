package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code psp-clearing-receivable:BRL} is a single global account shared across every payment (not merchant-scoped
 * like {@code merchant-payable:*}), so it accumulates balance across every test in this class and the suite.
 * Assertions on it compare deltas (balance before vs. after an operation) rather than absolute totals; the
 * merchant-payable account is freshly generated per test and can be asserted on directly.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LedgerProjectionIntegrationTests {

	private static final AccountId RECEIVABLE = AccountId.pspClearingReceivable(Currency.BRL);

	@Autowired
	private LedgerPostingPort ledgerPostingPort;

	@Autowired
	private LedgerProjectionPort ledgerProjectionPort;

	@Test
	void returnsEmptyForAnAccountThatNeverPosted() {
		AccountId neverUsed = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		assertThat(ledgerProjectionPort.currentBalance(neverUsed)).isEmpty();
	}

	@Test
	void aFullCaptureLeavesTheExpectedBalancesPerLedgerPostingsExample() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		long receivableBefore = receivableBalanceMinor();

		post(RECEIVABLE, payable, 10_000);

		assertThat(receivableDeltaSince(receivableBefore)).isEqualTo(10_000L);
		assertThat(ledgerProjectionPort.currentBalance(payable)).contains(Money.positive(10_000, Currency.BRL));
	}

	@Test
	void aPartialRefundNetsAgainstTheCaptureBalance() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		long receivableBefore = receivableBalanceMinor();
		post(RECEIVABLE, payable, 10_000);

		refund(payable, RECEIVABLE, 3_000);

		assertThat(receivableDeltaSince(receivableBefore)).isEqualTo(7_000L);
		assertThat(ledgerProjectionPort.currentBalance(payable)).contains(Money.positive(7_000, Currency.BRL));
	}

	@Test
	void aFinalRefundReturnsBothBalancesToZero() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		long receivableBefore = receivableBalanceMinor();
		post(RECEIVABLE, payable, 10_000);
		refund(payable, RECEIVABLE, 3_000);

		refund(payable, RECEIVABLE, 7_000);

		assertThat(receivableDeltaSince(receivableBefore)).isZero();
		assertThat(ledgerProjectionPort.currentBalance(payable)).contains(Money.zero(Currency.BRL));
	}

	@Test
	void rebuildingFromAnEmptyProjectionReproducesTheSameBalances() {
		AccountId payable = AccountId.merchantPayable(UUID.randomUUID(), Currency.BRL);
		post(RECEIVABLE, payable, 4_000);
		post(RECEIVABLE, payable, 6_000);
		refund(payable, RECEIVABLE, 2_500);

		Money receivableBeforeRebuild = ledgerProjectionPort.currentBalance(RECEIVABLE).orElseThrow();
		Money payableBeforeRebuild = ledgerProjectionPort.currentBalance(payable).orElseThrow();

		ledgerProjectionPort.rebuild();

		assertThat(ledgerProjectionPort.currentBalance(RECEIVABLE)).contains(receivableBeforeRebuild);
		assertThat(ledgerProjectionPort.currentBalance(payable)).contains(payableBeforeRebuild);
		assertThat(payableBeforeRebuild).isEqualTo(Money.positive(7_500, Currency.BRL));
	}

	private long receivableBalanceMinor() {
		return ledgerProjectionPort.currentBalance(RECEIVABLE).map(Money::amountInMinorUnits).orElse(0L);
	}

	private long receivableDeltaSince(long before) {
		return receivableBalanceMinor() - before;
	}

	private void post(AccountId debitAccount, AccountId creditAccount, long amountMinor) {
		ledgerPostingPort.post(LedgerTransaction.post(
			LedgerTransactionId.generate(), "capture:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(debitAccount, EntryDirection.DEBIT, Money.positive(amountMinor, Currency.BRL)),
				new LedgerEntry(creditAccount, EntryDirection.CREDIT, Money.positive(amountMinor, Currency.BRL))
			),
			Instant.now()
		));
	}

	private void refund(AccountId debitAccount, AccountId creditAccount, long amountMinor) {
		ledgerPostingPort.post(LedgerTransaction.post(
			LedgerTransactionId.generate(), "refund:" + UUID.randomUUID(), Currency.BRL,
			List.of(
				new LedgerEntry(debitAccount, EntryDirection.DEBIT, Money.positive(amountMinor, Currency.BRL)),
				new LedgerEntry(creditAccount, EntryDirection.CREDIT, Money.positive(amountMinor, Currency.BRL))
			),
			Instant.now()
		));
	}
}
