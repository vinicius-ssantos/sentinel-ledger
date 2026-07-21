package io.github.vinicius.sentinel.payments.internal.web;

final class InvalidPaymentIntentAmountException extends RuntimeException {

	InvalidPaymentIntentAmountException(String detail) {
		super(detail);
	}
}
