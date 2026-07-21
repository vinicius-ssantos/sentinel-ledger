package io.github.vinicius.sentinel.payments.internal;

record CanonicalCreatePaymentIntentCommand(String amountInMinorUnits, String currency) {
}
