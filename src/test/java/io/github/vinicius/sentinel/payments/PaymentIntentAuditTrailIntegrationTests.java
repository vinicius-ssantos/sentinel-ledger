package io.github.vinicius.sentinel.payments;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.audit.AuditActorType;
import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditTrailPort;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.merchant.MerchantId;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves AUD-001 for the payments module: every sensitive command (create, authorize, capture, refund) leaves
 * exactly one audit event, recorded in the same local transaction as the business effect it explains, and a
 * replayed request never adds a second one.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentIntentAuditTrailIntegrationTests {

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
	private AuditTrailPort auditTrailPort;

	@Autowired
	private SimulatedPspControls pspControls;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void createAuthorizeCaptureAndRefundEachLeaveExactlyOneOrderedAuditEvent() {
		ApiResult created = paymentIntentCommandService.create(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), "10000", "BRL", URI.create("/api/v1/payment-intents")
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
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, "10000", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		assertThat(captured.status()).isEqualTo(200);

		ApiResult refunded = refundPaymentIntentService.refund(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, "4000", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/refunds")
		);
		assertThat(refunded.status()).isEqualTo(200);

		List<AuditEvent> events = auditTrailPort.findByResource("payment_intent", paymentIntentId.value().toString());
		assertThat(events).extracting(AuditEvent::action).containsExactly(
			"payment-intent.create", "payment-intent.authorize", "payment-intent.capture", "payment-intent.refund"
		);
		assertThat(events).allSatisfy(event -> {
			assertThat(event.actor().type()).isEqualTo(AuditActorType.MERCHANT);
			assertThat(event.actor().id()).isEqualTo(MERCHANT_ID.value().toString());
			assertThat(event.resourceType()).isEqualTo("payment_intent");
			assertThat(event.resourceId()).isEqualTo(paymentIntentId.value().toString());
		});
	}

	@Test
	void retryingACaptureWithTheSameIdempotencyKeyDoesNotDuplicateItsAuditEvent() {
		ApiResult created = paymentIntentCommandService.create(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), "10000", "BRL", URI.create("/api/v1/payment-intents")
		);
		String location = created.location();
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.fromString(location.substring(location.lastIndexOf('/') + 1)));
		authorizePaymentIntentService.authorize(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);

		IdempotencyKey captureKey = new IdempotencyKey(UUID.randomUUID().toString());
		ApiResult first = capturePaymentIntentService.capture(
			MERCHANT_ID, captureKey, paymentIntentId, "10000", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		ApiResult replay = capturePaymentIntentService.capture(
			MERCHANT_ID, captureKey, paymentIntentId, "10000", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		assertThat(first.status()).isEqualTo(200);
		assertThat(replay.status()).isEqualTo(200);
		assertThat(replay.replayed()).isTrue();

		List<AuditEvent> captureEvents = auditTrailPort.findByResource("payment_intent", paymentIntentId.value().toString())
			.stream()
			.filter(event -> event.action().equals("payment-intent.capture"))
			.toList();
		assertThat(captureEvents).hasSize(1);
		assertThat(captureEvents.getFirst().correlationId()).isEqualTo(captureKey.value());
	}
}
