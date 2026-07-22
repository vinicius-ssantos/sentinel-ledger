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
import io.github.vinicius.sentinel.payments.internal.RefundPaymentIntentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Closes the gap left by {@link CapturePaymentIntentConcurrencyIntegrationTests} and
 * {@link RefundPaymentIntentConcurrencyIntegrationTests}: those races each request with a fresh idempotency key to
 * prove the amount-limit invariants (PAY-001/PAY-002). This class instead sends every racer through the exact same
 * Idempotency-Key, proving IDEM-001 ("one merchant, operation, and idempotency key produces at most one business
 * effect") through the full command service and ledger, not just the raw {@code IdempotencyGateway} acquisition
 * proven in JdbcIdempotencyGatewayIntegrationTests. A racer that observes IDEMPOTENCY_REQUEST_IN_PROGRESS retries
 * the identical key, mirroring how a real client is expected to behave.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CaptureAndRefundIdempotentConcurrencyIntegrationTests {

	private static final MerchantId MERCHANT_ID = new MerchantId(UUID.randomUUID());

	@Autowired
	private PaymentIntentCommandService paymentIntentCommandService;

	@Autowired
	private AuthorizePaymentIntentService authorizePaymentIntentService;

	@Autowired
	private CapturePaymentIntentService capturePaymentIntentService;

	@Autowired
	private RefundPaymentIntentService refundPaymentIntentService;

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
	void concurrentCapturesWithTheIdenticalIdempotencyKeyProduceExactlyOneLedgerEffect() throws Exception {
		int racers = 10;
		long captureAmount = 5_000;

		PaymentIntentId paymentIntentId = createAndAuthorize(captureAmount);
		IdempotencyKey sharedKey = new IdempotencyKey(UUID.randomUUID().toString());
		AccountId payable = AccountId.merchantPayable(MERCHANT_ID.value(), Currency.BRL);
		long payableBefore = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L);

		List<ApiResult> results = raceWithSharedKey(racers, sharedKey, () -> capturePaymentIntentService.capture(
			MERCHANT_ID, sharedKey, paymentIntentId, String.valueOf(captureAmount), "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		));

		assertThat(results).allMatch(result -> result.status() == 200);
		Set<String> distinctBodies = results.stream().map(ApiResult::body).collect(Collectors.toSet());
		assertThat(distinctBodies).hasSize(1);

		PaymentIntent finalState = paymentIntentStore.findOwned(paymentIntentId, MERCHANT_ID).orElseThrow();
		assertThat(finalState.capturedAmount()).isEqualTo(Money.positive(captureAmount, Currency.BRL));

		long payableDelta = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L) - payableBefore;
		assertThat(payableDelta).isEqualTo(captureAmount);
	}

	@Test
	void concurrentRefundsWithTheIdenticalIdempotencyKeyProduceExactlyOneLedgerEffect() throws Exception {
		int racers = 10;
		long captureAmount = 8_000;
		long refundAmount = 3_000;

		PaymentIntentId paymentIntentId = createAndAuthorize(captureAmount);
		ApiResult captured = capturePaymentIntentService.capture(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			String.valueOf(captureAmount), "BRL", URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		assertThat(captured.status()).isEqualTo(200);

		IdempotencyKey sharedKey = new IdempotencyKey(UUID.randomUUID().toString());
		AccountId payable = AccountId.merchantPayable(MERCHANT_ID.value(), Currency.BRL);
		long payableBefore = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L);

		List<ApiResult> results = raceWithSharedKey(racers, sharedKey, () -> refundPaymentIntentService.refund(
			MERCHANT_ID, sharedKey, paymentIntentId, String.valueOf(refundAmount), "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/refunds")
		));

		assertThat(results).allMatch(result -> result.status() == 200);
		Set<String> distinctBodies = results.stream().map(ApiResult::body).collect(Collectors.toSet());
		assertThat(distinctBodies).hasSize(1);

		PaymentIntent finalState = paymentIntentStore.findOwned(paymentIntentId, MERCHANT_ID).orElseThrow();
		assertThat(finalState.refundedAmount()).isEqualTo(Money.positive(refundAmount, Currency.BRL));

		long payableDelta = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L) - payableBefore;
		assertThat(payableDelta).isEqualTo(-refundAmount);
	}

	private List<ApiResult> raceWithSharedKey(int racers, IdempotencyKey sharedKey, Callable<ApiResult> attempt) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(racers);
		CyclicBarrier barrier = new CyclicBarrier(racers);
		try {
			List<Callable<ApiResult>> tasks = new ArrayList<>();
			for (int i = 0; i < racers; i++) {
				tasks.add(() -> {
					barrier.await();
					return retryUntilResolved(attempt);
				});
			}
			List<Future<ApiResult>> futures = executor.invokeAll(tasks);
			List<ApiResult> results = new ArrayList<>();
			for (Future<ApiResult> future : futures) {
				results.add(future.get());
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	private ApiResult retryUntilResolved(Callable<ApiResult> attempt) throws Exception {
		for (int i = 0; i < 100; i++) {
			ApiResult result = attempt.call();
			if (result.status() != 409 || !result.body().contains("IDEMPOTENCY_REQUEST_IN_PROGRESS")) {
				return result;
			}
			Thread.sleep(10);
		}
		throw new AssertionError("request never resolved past IDEMPOTENCY_REQUEST_IN_PROGRESS");
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
