package io.github.vinicius.sentinel.payments;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral callback evidence for one attempt. {@code sequence} is monotonic per attempt and only advances
 * when the underlying provider outcome actually changes, so a consumer can detect duplicate delivery (same
 * sequence) and out-of-order delivery (a lower sequence arriving after a higher one) without depending on wall-clock
 * delivery order.
 */
public record PspCallback(PspAttemptId attemptId, PspAuthorizationResult result, long sequence, Instant occurredAt) {

	public PspCallback {
		Objects.requireNonNull(attemptId, "attemptId must not be null");
		Objects.requireNonNull(result, "result must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		if (sequence < 1) {
			throw new IllegalArgumentException("sequence must be positive");
		}
	}
}
