package io.github.vinicius.sentinel.payments.internal.web;

final class UnsupportedCurrencyException extends RuntimeException {

	UnsupportedCurrencyException(String currency) {
		super("currency is not supported by the MVP: " + currency);
	}
}
