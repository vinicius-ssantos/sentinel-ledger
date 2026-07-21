package io.github.vinicius.sentinel.idempotency;

import java.util.Objects;
import java.util.regex.Pattern;

public record IdempotencyKey(String value) {

	private static final Pattern VALID_KEY = Pattern.compile("[\\x21-\\x7E]{16,128}");

	public IdempotencyKey {
		Objects.requireNonNull(value, "idempotency key must not be null");
		if (!VALID_KEY.matcher(value).matches()) {
			throw new IllegalArgumentException("idempotency key must be 16-128 visible ASCII characters");
		}
	}
}
