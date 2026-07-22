package io.github.vinicius.sentinel.payments.internal.web;

final class InvalidLedgerCursorException extends RuntimeException {

	InvalidLedgerCursorException() {
		super("the cursor query parameter is not a value this API previously returned");
	}
}
