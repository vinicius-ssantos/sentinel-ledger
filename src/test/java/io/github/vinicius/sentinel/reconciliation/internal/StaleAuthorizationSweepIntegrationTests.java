package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentState;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.payments.PspProviderReference;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.AuthorizePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * sentinel.reconciliation.stale-after=PT0S makes every uncertain authorization immediately eligible, so the sweep
 * can be proven deterministically without waiting for the real default threshold.
 */
@SpringBootTest(properties = "sentinel.reconciliation.stale-after=PT0S")
@Import(TestcontainersConfiguration.class)
class StaleAuthorizationSweepIntegrationTests {

	private static final MerchantId MERCHANT_ID = new MerchantId(UUID.randomUUID());

	@Autowired
	private PaymentIntentCommandService paymentIntentCommandService;

	@Autowired
	private AuthorizePaymentIntentService authorizePaymentIntentService;

	@Autowired
	private PaymentIntentStore paymentIntentStore;

	@Autowired
	private StaleAuthorizationSweep sweep;

	@Autowired
	private SimulatedPspControls pspControls;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void recoversAStuckUncertainAuthorizationWithoutTheOriginalClientRetrying() {
		ApiResult created = paymentIntentCommandService.create(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), "2500", "BRL", URI.create("/api/v1/payment-intents")
		);
		String location = created.location();
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.fromString(location.substring(location.lastIndexOf('/') + 1)));

		pspControls.programNextAttempt(
			paymentIntentId,
			new PspAuthorizationResult.Unknown(),
			new PspAuthorizationResult.Approved(new PspProviderReference("ref-sweep"))
		);
		authorizePaymentIntentService.authorize(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);
		assertThat(paymentIntentStore.findById(paymentIntentId).orElseThrow().state()).isEqualTo(PaymentIntentState.AUTHORIZATION_UNKNOWN);

		sweep.sweep();

		assertThat(paymentIntentStore.findById(paymentIntentId).orElseThrow().state()).isEqualTo(PaymentIntentState.AUTHORIZED);
	}
}
