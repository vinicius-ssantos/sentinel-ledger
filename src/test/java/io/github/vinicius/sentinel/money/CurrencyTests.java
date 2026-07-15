package io.github.vinicius.sentinel.money;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CurrencyTests {

	@Test
	void definesBrlWithTwoFractionDigits() {
		assertThat(Currency.BRL.code()).isEqualTo("BRL");
		assertThat(Currency.BRL.fractionDigits()).isEqualTo(2);
	}

	@Test
	void normalizesTheCurrencyCode() {
		assertThat(new Currency("brl", 2)).isEqualTo(Currency.BRL);
	}

	@Test
	void rejectsInvalidCurrencyCodes() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Currency("BR", 2));
		assertThatIllegalArgumentException().isThrownBy(() -> new Currency("R$L", 2));
		assertThatNullPointerException().isThrownBy(() -> new Currency(null, 2));
	}

	@Test
	void rejectsUnsupportedFractionDigitRanges() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Currency("BRL", -1));
		assertThatIllegalArgumentException().isThrownBy(() -> new Currency("BRL", 4));
	}
}
