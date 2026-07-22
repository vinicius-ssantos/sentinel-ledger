package io.github.vinicius.sentinel.reconciliation;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.LedgerProjectionPort;
import io.github.vinicius.sentinel.merchant.MerchantId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.internal.ApiResult;
import io.github.vinicius.sentinel.payments.internal.AuthorizePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.CapturePaymentIntentService;
import io.github.vinicius.sentinel.payments.internal.PaymentIntentCommandService;
import io.github.vinicius.sentinel.reconciliation.internal.ReconciliationResolutionService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReconciliationResolutionServiceIntegrationTests {

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
	private LedgerProjectionPort ledgerProjectionPort;

	@Autowired
	private SimulatedPspControls pspControls;

	@BeforeEach
	void resetPsp() {
		pspControls.reset();
	}

	@Test
	void compensateReversesTheNetCapturedAmountWithANewLedgerTransaction() {
		PaymentIntentId paymentIntentId = createAuthorizeAndCapture("7000");
		ReconciliationCase openCase = openCaseFor(paymentIntentId, "CAPTURED", "DECLINED:x");
		AccountId payable = AccountId.merchantPayable(MERCHANT_ID.value(), Currency.BRL);
		long balanceBefore = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L);

		ReconciliationCase resolved = reconciliationResolutionService.resolve(
			openCase.id(), new OperatorId(UUID.randomUUID()), "confirmed with provider, funds never settled",
			ReconciliationResolutionAction.COMPENSATE
		);

		assertThat(resolved.status()).isEqualTo(ReconciliationCaseStatus.RESOLVED);
		assertThat(resolved.resolutionIfPresent()).isPresent();
		assertThat(resolved.resolutionIfPresent().get().compensatingTransactionReference()).startsWith("reconciliation-correction:");

		long balanceAfter = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L);
		assertThat(balanceAfter - balanceBefore).isEqualTo(-7_000L);
	}

	@Test
	void acknowledgeNoActionResolvesWithoutTouchingTheLedger() {
		PaymentIntentId paymentIntentId = createAuthorizeAndCapture("3000");
		ReconciliationCase openCase = openCaseFor(paymentIntentId, "CAPTURED", "DECLINED:x");
		AccountId payable = AccountId.merchantPayable(MERCHANT_ID.value(), Currency.BRL);
		long balanceBefore = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L);

		ReconciliationCase resolved = reconciliationResolutionService.resolve(
			openCase.id(), new OperatorId(UUID.randomUUID()), "confirmed false positive", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION
		);

		assertThat(resolved.status()).isEqualTo(ReconciliationCaseStatus.RESOLVED);
		assertThat(resolved.resolutionIfPresent().get().compensatingTransactionReference()).isNull();
		long balanceAfter = ledgerProjectionPort.currentBalance(payable).map(Money::amountInMinorUnits).orElse(0L);
		assertThat(balanceAfter).isEqualTo(balanceBefore);
	}

	@Test
	void resolvingAnAlreadyResolvedCaseIsRejected() {
		PaymentIntentId paymentIntentId = createAuthorizeAndCapture("1000");
		ReconciliationCase openCase = openCaseFor(paymentIntentId, "CAPTURED", "DECLINED:x");
		OperatorId operatorId = new OperatorId(UUID.randomUUID());
		reconciliationResolutionService.resolve(openCase.id(), operatorId, "first resolution", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION);

		assertThatThrownBy(() -> reconciliationResolutionService.resolve(
			openCase.id(), operatorId, "second attempt", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION
		)).isInstanceOf(IllegalStateException.class);
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
