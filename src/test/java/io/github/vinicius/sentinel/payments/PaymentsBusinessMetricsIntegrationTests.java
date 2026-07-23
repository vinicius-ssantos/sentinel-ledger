package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.AuthorizePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.CapturePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import io.github.vinicius.sentinel.payments.internal.RefundPaymentIntentService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the business metrics from issue #25 are actually recorded, tagged only with fixed, bounded values -- a
 * payment or merchant identifier as a tag would let cardinality grow with traffic, which is exactly what
 * {@code MetricCardinalitySafetyTests} guards against structurally.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentsBusinessMetricsIntegrationTests {

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
	private SimulatedPspControls pspControls;

	@Autowired
	private MeterRegistry meterRegistry;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void aFullCaptureAndRefundIncrementsTheExpectedCounters() {
		double authorizedBefore = counterCount("sentinel.payments.authorization.result", "outcome", "AUTHORIZED");
		double captureSuccessBefore = counterCount("sentinel.payments.capture.result", "outcome", "SUCCESS");
		double refundSuccessBefore = counterCount("sentinel.payments.refund.result", "outcome", "SUCCESS");

		PaymentIntentId paymentIntentId = createAndAuthorize("5000");
		capture(paymentIntentId, "5000");
		refund(paymentIntentId, "2000");

		assertThat(counterCount("sentinel.payments.authorization.result", "outcome", "AUTHORIZED")).isEqualTo(authorizedBefore + 1);
		assertThat(counterCount("sentinel.payments.capture.result", "outcome", "SUCCESS")).isEqualTo(captureSuccessBefore + 1);
		assertThat(counterCount("sentinel.payments.refund.result", "outcome", "SUCCESS")).isEqualTo(refundSuccessBefore + 1);
	}

	@Test
	void aCaptureExceedingTheAuthorizedAmountIncrementsTheDeniedCounterWithABoundedReason() {
		double deniedBefore = counterCount(
			"sentinel.payments.capture.result", "outcome", "DENIED", "reason", "CAPTURE_EXCEEDS_AUTHORIZED_AMOUNT"
		);
		PaymentIntentId paymentIntentId = createAndAuthorize("1000");

		ApiResult result = capturePaymentIntentService.capture(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, "1001", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);

		assertThat(result.status()).isEqualTo(409);
		assertThat(counterCount(
			"sentinel.payments.capture.result", "outcome", "DENIED", "reason", "CAPTURE_EXCEEDS_AUTHORIZED_AMOUNT"
		)).isEqualTo(deniedBefore + 1);
	}

	private double counterCount(String name, String... tags) {
		var counter = meterRegistry.find(name).tags(tags).counter();
		return counter == null ? 0 : counter.count();
	}

	private void capture(PaymentIntentId paymentIntentId, String amountInMinorUnits) {
		ApiResult result = capturePaymentIntentService.capture(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, amountInMinorUnits, "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		assertThat(result.status()).isEqualTo(200);
	}

	private void refund(PaymentIntentId paymentIntentId, String amountInMinorUnits) {
		ApiResult result = refundPaymentIntentService.refund(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, amountInMinorUnits, "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/refunds")
		);
		assertThat(result.status()).isEqualTo(200);
	}

	private PaymentIntentId createAndAuthorize(String amountInMinorUnits) {
		ApiResult created = paymentIntentCommandService.create(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), amountInMinorUnits, "BRL", URI.create("/api/v1/payment-intents")
		);
		assertThat(created.status()).isEqualTo(201);
		String location = created.location();
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.fromString(location.substring(location.lastIndexOf('/') + 1)));

		ApiResult authorized = authorizePaymentIntentService.authorize(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);
		assertThat(authorized.status()).isEqualTo(200);
		return paymentIntentId;
	}
}
