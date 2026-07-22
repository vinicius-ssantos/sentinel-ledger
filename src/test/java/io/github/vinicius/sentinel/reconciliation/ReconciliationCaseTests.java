package io.github.vinicius.sentinel.reconciliation;

import io.github.vinicius.sentinel.payments.PaymentIntentId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationCaseTests {

	private static final PaymentIntentId PAYMENT_INTENT_ID = new PaymentIntentId(UUID.randomUUID());

	@Test
	void anOpenCaseMustNotCarryAResolution() {
		assertThatThrownBy(() -> new ReconciliationCase(
			ReconciliationCaseId.generate(), PAYMENT_INTENT_ID, "fp", ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE,
			ReconciliationSeverity.LOW, ReconciliationCaseStatus.OPEN, "local", "provider", Instant.now(),
			new ReconciliationResolution(new OperatorId(UUID.randomUUID()), "reason", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION, null, Instant.now())
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aResolvedCaseMustCarryAResolution() {
		assertThatThrownBy(() -> new ReconciliationCase(
			ReconciliationCaseId.generate(), PAYMENT_INTENT_ID, "fp", ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE,
			ReconciliationSeverity.LOW, ReconciliationCaseStatus.RESOLVED, "local", "provider", Instant.now(), null
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aBlankFingerprintIsRejected() {
		assertThatThrownBy(() -> new ReconciliationCase(
			ReconciliationCaseId.generate(), PAYMENT_INTENT_ID, "  ", ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE,
			ReconciliationSeverity.LOW, ReconciliationCaseStatus.OPEN, "local", "provider", Instant.now(), null
		)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void fingerprintIsDeterministicForTheSameInputs() {
		String first = ReconciliationCase.fingerprint(
			PAYMENT_INTENT_ID, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE, "CAPTURED", "DECLINED:x"
		);
		String second = ReconciliationCase.fingerprint(
			PAYMENT_INTENT_ID, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE, "CAPTURED", "DECLINED:x"
		);
		assertThat(first).isEqualTo(second);
	}

	@Test
	void compensateResolutionRequiresACompensatingTransactionReference() {
		assertThatThrownBy(() -> new ReconciliationResolution(
			new OperatorId(UUID.randomUUID()), "reason", ReconciliationResolutionAction.COMPENSATE, null, Instant.now()
		)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void resolutionRejectsABlankReason() {
		assertThatThrownBy(() -> new ReconciliationResolution(
			new OperatorId(UUID.randomUUID()), " ", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION, null, Instant.now()
		)).isInstanceOf(IllegalArgumentException.class);
	}
}
