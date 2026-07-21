package io.github.vinicius.sentinel.idempotency;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalRequestHasherTests {

	private static final UUID MERCHANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Test
	void sameOperationMerchantAndCommandProduceTheSameHash() {
		record Command(String amountInMinorUnits, String currency) {}

		String first = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, new Command("1000", "BRL"));
		String second = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, new Command("1000", "BRL"));

		assertThat(first).isEqualTo(second);
		assertThat(first).hasSize(64);
	}

	@Test
	void memberOrderingDoesNotAffectTheHash() {
		Map<String, Object> orderedOneWay = new LinkedHashMap<>();
		orderedOneWay.put("currency", "BRL");
		orderedOneWay.put("amountInMinorUnits", "1000");

		Map<String, Object> orderedTheOtherWay = new LinkedHashMap<>();
		orderedTheOtherWay.put("amountInMinorUnits", "1000");
		orderedTheOtherWay.put("currency", "BRL");

		String first = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, orderedOneWay);
		String second = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, orderedTheOtherWay);

		assertThat(first).isEqualTo(second);
	}

	@Test
	void aDifferentAmountProducesADifferentHash() {
		record Command(String amountInMinorUnits, String currency) {}

		String first = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, new Command("1000", "BRL"));
		String second = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, new Command("2000", "BRL"));

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void aDifferentMerchantProducesADifferentHash() {
		record Command(String amountInMinorUnits, String currency) {}
		UUID otherMerchant = UUID.fromString("22222222-2222-2222-2222-222222222222");

		String first = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, new Command("1000", "BRL"));
		String second = CanonicalRequestHasher.hash("payment-intent.create", otherMerchant, new Command("1000", "BRL"));

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void aDifferentOperationProducesADifferentHash() {
		record Command(String amountInMinorUnits, String currency) {}

		String first = CanonicalRequestHasher.hash("payment-intent.create", MERCHANT_ID, new Command("1000", "BRL"));
		String second = CanonicalRequestHasher.hash("payment-intent.authorize", MERCHANT_ID, new Command("1000", "BRL"));

		assertThat(first).isNotEqualTo(second);
	}
}
