package io.github.vinicius.sentinel.payments.internal.web;

final class PaymentIntentNotFoundException extends RuntimeException {

	PaymentIntentNotFoundException() {
		super("no payment intent exists for the authenticated merchant with the given identifier");
	}
}
