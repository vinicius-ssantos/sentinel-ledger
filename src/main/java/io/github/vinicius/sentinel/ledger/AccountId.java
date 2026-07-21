package io.github.vinicius.sentinel.ledger;

import io.github.vinicius.sentinel.money.Currency;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies an account in the MVP chart of accounts documented in docs/LEDGER_POSTINGS.md. The chart is
 * intentionally closed to two account families for the MVP, so the account type is derived from the identity
 * prefix instead of being tracked in a separate registry; fees, reserves, disputes, and multi-currency accounts
 * are out of scope and would need a new family (and an ADR) rather than a generic account constructor.
 */
public record AccountId(String value) {

	private static final String PSP_CLEARING_RECEIVABLE_PREFIX = "psp-clearing-receivable:";
	private static final String MERCHANT_PAYABLE_PREFIX = "merchant-payable:";

	public AccountId {
		Objects.requireNonNull(value, "account id must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException("account id must not be blank");
		}
	}

	public static AccountId pspClearingReceivable(Currency currency) {
		Objects.requireNonNull(currency, "currency must not be null");
		return new AccountId(PSP_CLEARING_RECEIVABLE_PREFIX + currency.code());
	}

	public static AccountId merchantPayable(UUID merchantId, Currency currency) {
		Objects.requireNonNull(merchantId, "merchantId must not be null");
		Objects.requireNonNull(currency, "currency must not be null");
		return new AccountId(MERCHANT_PAYABLE_PREFIX + merchantId + ":" + currency.code());
	}

	public AccountType type() {
		if (value.startsWith(PSP_CLEARING_RECEIVABLE_PREFIX)) {
			return AccountType.ASSET;
		}
		if (value.startsWith(MERCHANT_PAYABLE_PREFIX)) {
			return AccountType.LIABILITY;
		}
		throw new IllegalStateException("account id does not belong to the MVP chart of accounts: " + value);
	}
}
