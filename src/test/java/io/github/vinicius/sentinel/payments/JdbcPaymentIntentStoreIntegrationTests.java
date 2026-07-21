package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcPaymentIntentStoreIntegrationTests {

	@Autowired
	private PaymentIntentStore paymentIntentStore;

	@Test
	void savesAndReadsBackAnOwnedPaymentIntent() {
		MerchantId merchantId = new MerchantId(UUID.randomUUID());
		Money amount = Money.positive(12_50, Currency.BRL);
		PaymentIntent created = PaymentIntent.create(PaymentIntentId.generate(), merchantId, amount, Instant.now());

		paymentIntentStore.save(created);
		Optional<PaymentIntent> found = paymentIntentStore.findOwned(created.id(), merchantId);

		assertThat(found).isPresent();
		PaymentIntent restored = found.orElseThrow();
		assertThat(restored.id()).isEqualTo(created.id());
		assertThat(restored.merchantId()).isEqualTo(merchantId);
		assertThat(restored.amount()).isEqualTo(amount);
		assertThat(restored.state()).isEqualTo(PaymentIntentState.CREATED);
		assertThat(restored.capturedAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(restored.refundedAmount()).isEqualTo(Money.zero(Currency.BRL));
		assertThat(restored.version()).isEqualTo(created.version());
		assertThat(restored.createdAt()).isCloseTo(created.createdAt(), within(1, ChronoUnit.MICROS));
		assertThat(restored.updatedAt()).isCloseTo(created.updatedAt(), within(1, ChronoUnit.MICROS));
	}

	@Test
	void hidesAPaymentIntentOwnedByAnotherMerchant() {
		MerchantId owner = new MerchantId(UUID.randomUUID());
		MerchantId stranger = new MerchantId(UUID.randomUUID());
		PaymentIntent created = PaymentIntent.create(
			PaymentIntentId.generate(), owner, Money.positive(500, Currency.BRL), Instant.now()
		);

		paymentIntentStore.save(created);

		assertThat(paymentIntentStore.findOwned(created.id(), stranger)).isEmpty();
	}

	@Test
	void returnsEmptyForAnUnknownPaymentIntent() {
		assertThat(paymentIntentStore.findOwned(PaymentIntentId.generate(), new MerchantId(UUID.randomUUID())))
			.isEmpty();
	}

	@Test
	void savingAnUpdatedAggregateOverwritesTheStoredRow() {
		MerchantId merchantId = new MerchantId(UUID.randomUUID());
		PaymentIntent created = PaymentIntent.create(
			PaymentIntentId.generate(), merchantId, Money.positive(500, Currency.BRL), Instant.now()
		);
		paymentIntentStore.save(created);

		created.startAuthorization(Instant.now());
		paymentIntentStore.save(created);

		PaymentIntent restored = paymentIntentStore.findOwned(created.id(), merchantId).orElseThrow();
		assertThat(restored.state()).isEqualTo(PaymentIntentState.AUTHORIZATION_PENDING);
		assertThat(restored.version()).isEqualTo(1L);
	}

	@Test
	void savingAStaleVersionIsRejected() {
		MerchantId merchantId = new MerchantId(UUID.randomUUID());
		PaymentIntent created = PaymentIntent.create(
			PaymentIntentId.generate(), merchantId, Money.positive(500, Currency.BRL), Instant.now()
		);
		paymentIntentStore.save(created);

		PaymentIntent staleCopy = paymentIntentStore.findOwned(created.id(), merchantId).orElseThrow();
		created.startAuthorization(Instant.now());
		paymentIntentStore.save(created);

		staleCopy.cancel(Instant.now());

		assertThatThrownBy(() -> paymentIntentStore.save(staleCopy))
			.isInstanceOf(OptimisticLockException.class);
	}
}
