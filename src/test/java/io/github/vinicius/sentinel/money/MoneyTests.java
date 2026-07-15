package io.github.vinicius.sentinel.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatArithmeticException;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MoneyTests {

	private static final Currency USD = new Currency("USD", 2);

	@Test
	void createsSignedZeroPositiveAndNonNegativeAmountsExplicitly() {
		assertThat(Money.ofMinor(-100, Currency.BRL).isNegative()).isTrue();
		assertThat(Money.zero(Currency.BRL).isZero()).isTrue();
		assertThat(Money.positive(1, Currency.BRL).isPositive()).isTrue();
		assertThat(Money.nonNegative(0, Currency.BRL)).isEqualTo(Money.zero(Currency.BRL));

		assertThatIllegalArgumentException().isThrownBy(() -> Money.positive(0, Currency.BRL));
		assertThatIllegalArgumentException().isThrownBy(() -> Money.positive(-1, Currency.BRL));
		assertThatIllegalArgumentException().isThrownBy(() -> Money.nonNegative(-1, Currency.BRL));
	}

	@Test
	void addsSubtractsAndNegatesUsingExactIntegerArithmetic() {
		Money amount = Money.ofMinor(1_500, Currency.BRL);

		assertThat(amount.add(Money.ofMinor(250, Currency.BRL)))
			.isEqualTo(Money.ofMinor(1_750, Currency.BRL));
		assertThat(amount.subtract(Money.ofMinor(2_000, Currency.BRL)))
			.isEqualTo(Money.ofMinor(-500, Currency.BRL));
		assertThat(amount.negate()).isEqualTo(Money.ofMinor(-1_500, Currency.BRL));
	}

	@Test
	void rejectsCrossCurrencyArithmeticAndComparison() {
		Money brl = Money.ofMinor(100, Currency.BRL);
		Money usd = Money.ofMinor(100, USD);

		CurrencyMismatchException exception = catchThrowableOfType(
			() -> brl.add(usd),
			CurrencyMismatchException.class
		);

		assertThat(exception.left()).isEqualTo(Currency.BRL);
		assertThat(exception.right()).isEqualTo(USD);
		assertThatIllegalArgumentException().isThrownBy(() -> brl.subtract(usd));
		assertThatIllegalArgumentException().isThrownBy(() -> brl.compareTo(usd));
	}

	@Test
	void failsFastWhenLongArithmeticWouldOverflow() {
		assertThatArithmeticException().isThrownBy(() ->
			Money.ofMinor(Long.MAX_VALUE, Currency.BRL).add(Money.ofMinor(1, Currency.BRL))
		);
		assertThatArithmeticException().isThrownBy(() ->
			Money.ofMinor(Long.MIN_VALUE, Currency.BRL).subtract(Money.ofMinor(1, Currency.BRL))
		);
		assertThatArithmeticException().isThrownBy(() ->
			Money.ofMinor(Long.MIN_VALUE, Currency.BRL).negate()
		);
	}

	@Test
	void comparesAmountsOnlyWithinTheSameCurrency() {
		assertThat(Money.ofMinor(99, Currency.BRL))
			.isLessThan(Money.ofMinor(100, Currency.BRL));
		assertThat(Money.ofMinor(100, Currency.BRL))
			.isGreaterThan(Money.ofMinor(99, Currency.BRL));
	}

	@Test
	void exposesMinorUnitsAsLosslessBoundaryText() {
		assertThat(Money.ofMinor(Long.MAX_VALUE, Currency.BRL).amountInMinorUnitsText())
			.isEqualTo("9223372036854775807");
	}

	@Test
	void rejectsMissingCurrencyOrOperand() {
		assertThatNullPointerException().isThrownBy(() -> new Money(100, null));
		assertThatNullPointerException().isThrownBy(() -> Money.ofMinor(100, Currency.BRL).add(null));
	}
}
