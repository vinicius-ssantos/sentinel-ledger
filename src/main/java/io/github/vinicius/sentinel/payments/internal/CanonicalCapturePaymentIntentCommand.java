package io.github.vinicius.sentinel.payments.internal;

record CanonicalCapturePaymentIntentCommand(String paymentIntentId, String amountInMinorUnits, String currency) {
}
