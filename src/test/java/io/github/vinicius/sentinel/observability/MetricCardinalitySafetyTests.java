package io.github.vinicius.sentinel.observability;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.AuthorizePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.CapturePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import io.github.vinicius.sentinel.payments.internal.RefundPaymentIntentService;
import io.github.vinicius.sentinel.reconciliation.OperatorId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCase;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCasePort;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseStatus;
import io.github.vinicius.sentinel.reconciliation.ReconciliationMismatchType;
import io.github.vinicius.sentinel.reconciliation.ReconciliationOpenOutcome;
import io.github.vinicius.sentinel.reconciliation.ReconciliationResolutionAction;
import io.github.vinicius.sentinel.reconciliation.ReconciliationSeverity;
import io.github.vinicius.sentinel.reconciliation.internal.ReconciliationResolutionService;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OBS-001: no {@code sentinel.*} meter may be tagged by an unbounded business identifier -- that would let
 * cardinality grow with traffic, which is exactly what a reconciliation/payment dashboard cannot tolerate. Drives
 * one capture, one refund, and one reconciliation open/resolve itself first so every counter this codebase
 * registers lazily (on first use, inside the relevant service method) actually exists to be inspected, regardless
 * of what other test classes have or haven't run first in a shared context.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MetricCardinalitySafetyTests {

	private static final List<String> FORBIDDEN_TAG_KEY_SUBSTRINGS = List.of(
		"paymentintentid", "merchantid", "operatorid", "caseid", "transactionid", "deliveryid", "eventid",
		"attemptid", "correlationid", "idempotencykey"
	);
	private static final Pattern UUID_PATTERN =
		Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
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
	void noSentinelMeterIsTaggedByAnUnboundedBusinessIdentifier() {
		driveOneCaptureRefundAndReconciliationResolution();

		for (Meter meter : meterRegistry.getMeters()) {
			String meterName = meter.getId().getName();
			if (!meterName.startsWith("sentinel.")) {
				continue;
			}
			for (Tag tag : meter.getId().getTags()) {
				String lowerKey = tag.getKey().toLowerCase();
				assertThat(FORBIDDEN_TAG_KEY_SUBSTRINGS.stream().noneMatch(lowerKey::contains))
					.as("meter %s has a tag key '%s' that looks like an unbounded identifier", meterName, tag.getKey())
					.isTrue();
				assertThat(UUID_PATTERN.matcher(tag.getValue()).find())
					.as("meter %s has a tag %s=%s that looks like a UUID identifier", meterName, tag.getKey(), tag.getValue())
					.isFalse();
			}
		}
	}

	private void driveOneCaptureRefundAndReconciliationResolution() {
		ApiResult created = paymentIntentCommandService.create(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), "3000", "BRL", URI.create("/api/v1/payment-intents")
		);
		String location = created.location();
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.fromString(location.substring(location.lastIndexOf('/') + 1)));
		authorizePaymentIntentService.authorize(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);
		capturePaymentIntentService.capture(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, "3000", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		refundPaymentIntentService.refund(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, "1000", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/refunds")
		);

		String fingerprint = ReconciliationCase.fingerprint(
			paymentIntentId, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE, "PARTIALLY_REFUNDED", "DECLINED:x"
		);
		ReconciliationCase candidate = new ReconciliationCase(
			ReconciliationCaseId.generate(), paymentIntentId, fingerprint, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE,
			ReconciliationSeverity.HIGH, ReconciliationCaseStatus.OPEN, "PARTIALLY_REFUNDED", "DECLINED:x",
			Instant.now().truncatedTo(ChronoUnit.MICROS), null
		);
		ReconciliationOpenOutcome outcome = reconciliationCasePort.open(candidate);
		ReconciliationCase opened = outcome instanceof ReconciliationOpenOutcome.Opened o ? o.opened()
			: ((ReconciliationOpenOutcome.AlreadyOpen) outcome).existing();
		if (opened.status() == ReconciliationCaseStatus.OPEN || opened.status() == ReconciliationCaseStatus.INVESTIGATING) {
			reconciliationResolutionService.resolve(
				opened.id(), new OperatorId(UUID.randomUUID()), "metric coverage fixture", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION
			);
		}
	}
}
