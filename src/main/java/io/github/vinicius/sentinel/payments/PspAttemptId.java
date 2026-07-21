package io.github.vinicius.sentinel.payments;

import java.util.Objects;
import java.util.UUID;

/**
 * Client-generated identity for one provider call attempt. Unlike a provider-assigned reference, this identity is
 * known before the call is made, so status can be recovered even when the provider's synchronous response is lost.
 */
public record PspAttemptId(UUID value) {

	public PspAttemptId {
		Objects.requireNonNull(value, "attempt id must not be null");
	}

	public static PspAttemptId generate() {
		return new PspAttemptId(UUID.randomUUID());
	}
}
