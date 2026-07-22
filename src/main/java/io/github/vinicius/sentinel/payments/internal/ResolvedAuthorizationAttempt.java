package io.github.vinicius.sentinel.payments.internal;

import java.time.Instant;

record ResolvedAuthorizationAttempt(String outcome, String providerReference, String reasonCode, Instant occurredAt) {
}
