package io.github.vinicius.sentinel.payments;

import java.util.Objects;

public record PspProviderReference(String value) {

	public PspProviderReference {
		Objects.requireNonNull(value, "provider reference must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException("provider reference must not be blank");
		}
	}
}
