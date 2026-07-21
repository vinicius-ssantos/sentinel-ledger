package io.github.vinicius.sentinel.idempotency;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTests {

	@ParameterizedTest
	@ValueSource(strings = {
		"0123456789abcdef",
		"ABCDEFGHIJKLMNOP-abcdefghijklmnop",
		"11111111-1111-1111-1111-111111111111"
	})
	void acceptsSixteenToOneTwentyEightVisibleAsciiCharacters(String value) {
		assertThat(new IdempotencyKey(value).value()).isEqualTo(value);
	}

	@Test
	void rejectsAKeyShorterThanSixteenCharacters() {
		assertThatThrownBy(() -> new IdempotencyKey("short-key"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAKeyLongerThanOneTwentyEightCharacters() {
		assertThatThrownBy(() -> new IdempotencyKey("a".repeat(129)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsAKeyContainingWhitespace() {
		assertThatThrownBy(() -> new IdempotencyKey("valid-key-value with space"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsANullValue() {
		assertThatThrownBy(() -> new IdempotencyKey(null))
			.isInstanceOf(NullPointerException.class);
	}
}
