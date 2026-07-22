package io.github.vinicius.sentinel.reconciliation;

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
import io.github.vinicius.sentinel.payments.internal.CapturePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import io.github.vinicius.sentinel.reconciliation.internal.PaymentIntentReconciliationCheckService;
import io.github.vinicius.sentinel.reconciliation.internal.ReconciliationCheckOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PaymentIntentReconciliationCheckServiceIntegrationTests {

	private static final MerchantId MERCHANT_ID = new MerchantId(UUID.randomUUID());

	@Autowired
	private PaymentIntentCommandService paymentIntentCommandService;

	@Autowired
	private AuthorizePaymentIntentService authorizePaymentIntentService;

	@Autowired
	private CapturePaymentIntentService capturePaymentIntentService;

	@Autowired
	private PaymentIntentReconciliationCheckService checkService;

	@Autowired
	private PaymentIntentStore paymentIntentStore;

	@Autowired
	private SimulatedPspControls pspControls;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void aNeverAuthorizedPaymentIntentHasNoEvidenceToReconcile() {
		PaymentIntentId paymentIntentId = create("5000");

		ReconciliationCheckOutcome outcome = checkService.check(paymentIntentId);

		assertThat(outcome).isInstanceOf(ReconciliationCheckOutcome.NoEvidence.class);
	}

	@Test
	void aStuckUncertainAuthorizationIsSafelyAutoResolvedWithoutOpeningACase() {
		PaymentIntentId paymentIntentId = create("5000");
		pspControls.programNextAttempt(
			paymentIntentId,
			new PspAuthorizationResult.Unknown(),
			new PspAuthorizationResult.Approved(new PspProviderReference("ref-1"))
		);

		ApiResult authorized = authorizePaymentIntentService.authorize(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);
		assertThat(authorized.status()).isEqualTo(202);
		assertThat(paymentIntentStore.findById(paymentIntentId).orElseThrow().state()).isEqualTo(PaymentIntentState.AUTHORIZATION_UNKNOWN);

		ReconciliationCheckOutcome outcome = checkService.check(paymentIntentId);

		assertThat(outcome).isInstanceOf(ReconciliationCheckOutcome.AutoResolved.class);
		assertThat(((ReconciliationCheckOutcome.AutoResolved) outcome).resolvedState()).isEqualTo(PaymentIntentState.AUTHORIZED);
		assertThat(paymentIntentStore.findById(paymentIntentId).orElseThrow().state()).isEqualTo(PaymentIntentState.AUTHORIZED);
	}

	@Test
	void anAuthorizationConsistentWithProviderEvidenceNeedsNoAction() {
		PaymentIntentId paymentIntentId = createAndAuthorize("5000");

		ReconciliationCheckOutcome outcome = checkService.check(paymentIntentId);

		assertThat(outcome).isInstanceOf(ReconciliationCheckOutcome.AlreadyConsistent.class);
		assertThat(((ReconciliationCheckOutcome.AlreadyConsistent) outcome).state()).isEqualTo(PaymentIntentState.AUTHORIZED);
	}

	@Test
	void aCapturedPaymentThatTheProviderNowReportsDeclinedOpensAHighSeverityCase() {
		PaymentIntentId paymentIntentId = create("5000");
		pspControls.programNextAttempt(
			paymentIntentId,
			new PspAuthorizationResult.Approved(new PspProviderReference("ref-2")),
			new PspAuthorizationResult.Declined("later_declined")
		);
		authorizePaymentIntentService.authorize(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);
		ApiResult captured = capturePaymentIntentService.capture(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId, "5000", "BRL",
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/captures")
		);
		assertThat(captured.status()).isEqualTo(200);

		ReconciliationCheckOutcome first = checkService.check(paymentIntentId);
		ReconciliationCheckOutcome second = checkService.check(paymentIntentId);

		assertThat(first).isInstanceOf(ReconciliationCheckOutcome.MismatchDetected.class);
		ReconciliationCase firstCase = extractCase((ReconciliationCheckOutcome.MismatchDetected) first);
		assertThat(firstCase.severity()).isEqualTo(ReconciliationSeverity.HIGH);
		assertThat(firstCase.localEvidence()).isEqualTo("CAPTURED");
		assertThat(firstCase.providerEvidence()).contains("DECLINED");

		assertThat(second).isInstanceOf(ReconciliationCheckOutcome.MismatchDetected.class);
		ReconciliationCase secondCase = extractCase((ReconciliationCheckOutcome.MismatchDetected) second);
		assertThat(secondCase.id()).isEqualTo(firstCase.id());
	}

	private static ReconciliationCase extractCase(ReconciliationCheckOutcome.MismatchDetected mismatch) {
		return mismatch.outcome() instanceof ReconciliationOpenOutcome.Opened opened
			? opened.opened()
			: ((ReconciliationOpenOutcome.AlreadyOpen) mismatch.outcome()).existing();
	}

	private PaymentIntentId createAndAuthorize(String amountInMinorUnits) {
		PaymentIntentId paymentIntentId = create(amountInMinorUnits);
		ApiResult authorized = authorizePaymentIntentService.authorize(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), paymentIntentId,
			URI.create("/api/v1/payment-intents/" + paymentIntentId.value() + "/authorize")
		);
		assertThat(authorized.status()).isEqualTo(200);
		return paymentIntentId;
	}

	private PaymentIntentId create(String amountInMinorUnits) {
		ApiResult created = paymentIntentCommandService.create(
			MERCHANT_ID, new IdempotencyKey(UUID.randomUUID().toString()), amountInMinorUnits, "BRL", URI.create("/api/v1/payment-intents")
		);
		assertThat(created.status()).isEqualTo(201);
		String location = created.location();
		return new PaymentIntentId(UUID.fromString(location.substring(location.lastIndexOf('/') + 1)));
	}
}
