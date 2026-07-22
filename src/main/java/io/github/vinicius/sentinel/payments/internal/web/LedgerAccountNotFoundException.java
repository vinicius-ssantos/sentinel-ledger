package io.github.vinicius.sentinel.payments.internal.web;

final class LedgerAccountNotFoundException extends RuntimeException {

	LedgerAccountNotFoundException() {
		super("no ledger account exists for the authenticated merchant with the given identifier");
	}
}
