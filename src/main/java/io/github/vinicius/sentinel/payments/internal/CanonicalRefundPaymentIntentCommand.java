package io.github.vinicius.sentinel.payments.internal;

record CanonicalRefundPaymentIntentCommand(String paymentIntentId, String amountInMinorUnits, String currency) {
}
