package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.LedgerProjectionPort;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.AuthorizePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.CapturePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The concurrency proof required by docs/ARCHITECTURE.md and AGENTS.md: at least twenty simultaneous capture
 * requests against one authorization, asserting the final captured total never exceeds it. Every request goes
 * through {@link CapturePaymentIntentService#capture} directly (its own real transaction per call, via the Spring
 * proxy) rather than through MockMvc, so this is a persistence/optimistic-locking proof, not an HTTP concern.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CapturePaymentIntentConcurrencyIntegrationTests {

	private static final MerchantId MERCHANT_ID = new MerchantId(UUID.randomUUID());

	@Autowired
	private PaymentIntentCommandService paymentIntentCommandService;

	@Autowired
	private AuthorizePaymentIntentService authorizePaymentIntentService;

	@Autowired
	private CapturePaymentIntentService capturePaymentIntentService;

	@Autowired
	private PaymentIntentStore paymentIntentStore;

	@Autowired
	private LedgerProjectionPort ledgerProjectionPort;

	@Autowired
	private SimulatedPspControls pspControls;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void twentyConcurrentPartialCapturesNeverExceedTheAuthorizedAmountAndCanStillReachIt() throws Exception {
		int racers = 20;
		long captureAmount = 1_000;
		long authorizedAmount = racers * captureAmount;

		PaymentIntentId paymentIntentId = createAndAuthorize(authorizedAmount);
		AccountId payable = AccountId.merchantPayable(MERCHANT_ID.value(), Currency.BRL);
		long payableBefore = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L);

		ExecutorService executor = Executors.newFixedThreadPool(racers);
		CyclicBarrier barrier = new CyclicBarrier(racers);
		try {
			List<Callable<Void>> tasks = new ArrayList<>();
			for (int i = 0; i < racers; i++) {
				tasks.add(() -> {
					barrier.await();
					captureWithRetry(paymentIntentId, captureAmount);
					return null;
				});
			}
			List<Future<Void>> futures = executor.invokeAll(tasks);
			for (Future<Void> future : futures) {
				future.get();
			}
		} finally {
			executor.shutdownNow();
		}

		PaymentIntent finalState = paymentIntentStore.findOwned(paymentIntentId, MERCHANT_ID).orElseThrow();
		assertThat(finalState.capturedAmount()).isEqualTo(Money.positive(authorizedAmount, Currency.BRL));
		assertThat(finalState.state()).isEqualTo(PaymentIntentState.CAPTURED);

		long payableDelta = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L) - payableBefore;
		assertThat(payableDelta).isEqualTo(authorizedAmount);
	}

	private void captureWithRetry(PaymentIntentId paymentIntentId, long amountInMinorUnits) {
		for (int attempt = 0; attempt < 50; attempt++) {
			ApiResult result = capturePaymentIntentService.capture(
				MERCHANT_ID,
				new IdempotencyKey(UUID.randomUUID().toString()),
				paymentIntentId,
				String.valueOf(amountInMinorUnits),
				"BRL",
				URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
			);
			if (result.status() == 200) {
				return;
			}
			if (result.status() == 409 && result.body().contains("CAPTURE_LIMIT_EXCEEDED")) {
				return;
			}
			if (result.status() == 409 && result.body().contains("CONCURRENT_PAYMENT_MODIFICATION")) {
				continue;
			}
			throw new AssertionError("unexpected capture response: " + result.status() + " " + result.body());
		}
		throw new AssertionError("capture did not resolve after retries for " + paymentIntentId);
	}

	private PaymentIntentId createAndAuthorize(long amountInMinorUnits) {
		ApiResult created = paymentIntentCommandService.create(
			MERCHANT_ID,
			new IdempotencyKey(UUID.randomUUID().toString()),
			String.valueOf(amountInMinorUnits),
			"BRL",
			URI.create("/api/v1/payment-intents")
		);
		assertThat(created.status()).isEqualTo(201);
		String location = created.location();
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.fromString(location.substring(location.lastIndexOf('/') + 1)));

		ApiResult authorized = authorizePaymentIntentService.authorize(
			MERCHANT_ID,
			new IdempotencyKey(UUID.randomUUID().toString()),
			paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);
		assertThat(authorized.status()).isEqualTo(200);
		return paymentIntentId;
	}
}
