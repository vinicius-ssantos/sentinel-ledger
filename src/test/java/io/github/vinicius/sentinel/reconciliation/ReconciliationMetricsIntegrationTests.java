package io.github.vinicius.sentinel.reconciliation;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.AuthorizePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.CapturePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import io.github.vinicius.sentinel.reconciliation.internal.ReconciliationResolutionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WK-25 dashboard inputs: case-opened (by severity), case-resolved (by action), case age, and currently-open
 * gauges. The gauge is queried live (re-evaluated on read), so it reflects state directly rather than a counter
 * delta.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReconciliationMetricsIntegrationTests {

	private static final MerchantId MERCHANT_ID = new MerchantId(UUID.randomUUID());

	@Autowired
	private PaymentIntentCommandService paymentIntentCommandService;

	@Autowired
	private AuthorizePaymentIntentService authorizePaymentIntentService;

	@Autowired
	private CapturePaymentIntentService capturePaymentIntentService;

	@Autowired
	private ReconciliationCasePort reconciliationCasePort;

	@Autowired
	private ReconciliationResolutionService reconciliationResolutionService;

	@Autowired
	private SimulatedPspControls pspControls;

	@Autowired
	private MeterRegistry meterRegistry;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void openingACaseIncrementsTheOpenGaugeAndResolvingItIncrementsResolvedAndRecordsAge() {
		double openHighBefore = gaugeValue("sentinel.reconciliation.cases.open", "severity", "HIGH");
		double resolvedBefore = counterCount("sentinel.reconciliation.cases.resolved", "action", "ACKNOWLEDGE_NO_ACTION");

		PaymentIntentId paymentIntentId = createAuthorizeAndCapture("4000");
		ReconciliationCase openCase = openCaseFor(paymentIntentId, "CAPTURED", "DECLINED:x");

		assertThat(gaugeValue("sentinel.reconciliation.cases.open", "severity", "HIGH")).isEqualTo(openHighBefore + 1);

		reconciliationResolutionService.resolve(
			openCase.id(), new OperatorId(UUID.randomUUID()), "confirmed false positive", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION
		);

		assertThat(gaugeValue("sentinel.reconciliation.cases.open", "severity", "HIGH")).isEqualTo(openHighBefore);
		assertThat(counterCount("sentinel.reconciliation.cases.resolved", "action", "ACKNOWLEDGE_NO_ACTION")).isEqualTo(resolvedBefore + 1);
		var timer = meterRegistry.find("sentinel.reconciliation.case.age").tag("action", "ACKNOWLEDGE_NO_ACTION").timer();
		assertThat(timer).isNotNull();
		assertThat(timer.count()).isGreaterThanOrEqualTo(1);
	}

	private double gaugeValue(String name, String... tags) {
		var gauge = meterRegistry.find(name).tags(tags).gauge();
		return gauge == null ? 0 : gauge.value();
	}

	private double counterCount(String name, String... tags) {
		var counter = meterRegistry.find(name).tags(tags).counter();
		return counter == null ? 0 : counter.count();
	}

	private ReconciliationCase openCaseFor(PaymentIntentId paymentIntentId, String localEvidence, String providerEvidence) {
		String fingerprint = ReconciliationCase.fingerprint(
			paymentIntentId, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE, localEvidence, providerEvidence
		);
		ReconciliationCase candidate = new ReconciliationCase(
			ReconciliationCaseId.generate(), paymentIntentId, fingerprint, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE,
			ReconciliationSeverity.HIGH, ReconciliationCaseStatus.OPEN, localEvidence, providerEvidence,
			Instant.now().truncatedTo(ChronoUnit.MICROS), null
		);
		ReconciliationOpenOutcome outcome = reconciliationCasePort.open(candidate);
		return outcome instanceof ReconciliationOpenOutcome.Opened opened ? opened.opened()
			: ((ReconciliationOpenOutcome.AlreadyOpen) outcome).existing();
	}

	private PaymentIntentId createAuthorizeAndCapture(String amountInMinorUnits) {
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

		ApiResult captured = capturePaymentIntentService.capture(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, amountInMinorUnits, "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		assertThat(captured.status()).isEqualTo(200);

		return paymentIntentId;
	}
}
