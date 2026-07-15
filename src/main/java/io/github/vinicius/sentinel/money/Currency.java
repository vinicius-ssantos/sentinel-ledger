package io.github.vinicius.sentinel.money;

import java.util.Locale;
import java.util.Objects;

public record Currency(String code, int fractionDigits) {

	public static final Currency BRL = new Currency("BRL", 2);

	public Currency {
		Objects.requireNonNull(code, "code must not be null");
		code = code.toUpperCase(Locale.ROOT);
		if (!code.matches("[A-Z]{3}")) {
			throw new IllegalArgumentException("currency code must contain exactly three ASCII letters");
		}
		if (fractionDigits < 0 || fractionDigits > 3) {
			throw new IllegalArgumentException("currency fraction digits must be between 0 and 3");
		}
	}
}
