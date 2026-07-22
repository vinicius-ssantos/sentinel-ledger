package io.github.vinicius.sentinel.reconciliation.internal.web;

final class PaymentIntentNotFoundException extends RuntimeException {

	PaymentIntentNotFoundException() {
		super("no payment intent exists with the given identifier");
	}
}
