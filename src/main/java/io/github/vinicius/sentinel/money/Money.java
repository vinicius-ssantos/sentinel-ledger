package io.github.vinicius.sentinel.money;

import java.util.Objects;

public record Money(long amountInMinorUnits, Currency currency) implements Comparable<Money> {

	public Money {
		Objects.requireNonNull(currency, "currency must not be null");
	}

	public static Money ofMinor(long amountInMinorUnits, Currency currency) {
		return new Money(amountInMinorUnits, currency);
	}

	public static Money zero(Currency currency) {
		return new Money(0, currency);
	}

	public static Money positive(long amountInMinorUnits, Currency currency) {
		if (amountInMinorUnits <= 0) {
			throw new IllegalArgumentException("money amount must be positive");
		}
		return new Money(amountInMinorUnits, currency);
	}

	public static Money nonNegative(long amountInMinorUnits, Currency currency) {
		if (amountInMinorUnits < 0) {
			throw new IllegalArgumentException("money amount must not be negative");
		}
		return new Money(amountInMinorUnits, currency);
	}

	public Money add(Money other) {
		requireSameCurrency(other);
		return new Money(Math.addExact(amountInMinorUnits, other.amountInMinorUnits), currency);
	}

	public Money subtract(Money other) {
		requireSameCurrency(other);
		return new Money(Math.subtractExact(amountInMinorUnits, other.amountInMinorUnits), currency);
	}

	public Money negate() {
		return new Money(Math.negateExact(amountInMinorUnits), currency);
	}

	public boolean isZero() {
		return amountInMinorUnits == 0;
	}

	public boolean isPositive() {
		return amountInMinorUnits > 0;
	}

	public boolean isNegative() {
		return amountInMinorUnits < 0;
	}

	public String amountInMinorUnitsText() {
		return Long.toString(amountInMinorUnits);
	}

	@Override
	public int compareTo(Money other) {
		requireSameCurrency(other);
		return Long.compare(amountInMinorUnits, other.amountInMinorUnits);
	}

	private void requireSameCurrency(Money other) {
		Objects.requireNonNull(other, "other money must not be null");
		if (!currency.equals(other.currency)) {
			throw new CurrencyMismatchException(currency, other.currency);
		}
	}
}
