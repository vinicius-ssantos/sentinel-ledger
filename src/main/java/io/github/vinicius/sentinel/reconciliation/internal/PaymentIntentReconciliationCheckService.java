package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.payments.AuthorizationAttemptEvidencePort;
import io.github.vinicius.sentinel.payments.AuthorizationReconciliationPort;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PaymentIntentState;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationPort;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCase;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCasePort;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseStatus;
import io.github.vinicius.sentinel.reconciliation.ReconciliationMismatchType;
import io.github.vinicius.sentinel.reconciliation.ReconciliationOpenOutcome;
import io.github.vinicius.sentinel.reconciliation.ReconciliationSeverity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Compares one payment intent's local state against fresh provider evidence (checkStatus). A still-uncertain local
 * state (AUTHORIZATION_PENDING/UNKNOWN) is safely auto-resolved the same way a client retry would resolve it — no
 * human judgment required. A local state that already committed to a terminal outcome, but disagrees with the
 * provider, is never overwritten automatically: it becomes a {@link ReconciliationCase} for operator review,
 * preserving both pieces of evidence untouched (docs/ARCHITECTURE.md's reconciliation boundary).
 */
@Service
public class PaymentIntentReconciliationCheckService {

	private static final Set<PaymentIntentState> UNCERTAIN_STATES =
		Set.of(PaymentIntentState.AUTHORIZATION_PENDING, PaymentIntentState.AUTHORIZATION_UNKNOWN);
	private static final Set<PaymentIntentState> AUTHORIZED_FAMILY = Set.of(
		PaymentIntentState.AUTHORIZED, PaymentIntentState.PARTIALLY_CAPTURED, PaymentIntentState.CAPTURED,
		PaymentIntentState.PARTIALLY_REFUNDED, PaymentIntentState.REFUNDED
	);
	private static final Set<PaymentIntentState> FINANCIAL_STATES = Set.of(
		PaymentIntentState.PARTIALLY_CAPTURED, PaymentIntentState.CAPTURED,
		PaymentIntentState.PARTIALLY_REFUNDED, PaymentIntentState.REFUNDED
	);

	private final PaymentIntentStore paymentIntentStore;
	private final AuthorizationAttemptEvidencePort attemptEvidencePort;
	private final PspAuthorizationPort pspAuthorizationPort;
	private final AuthorizationReconciliationPort authorizationReconciliationPort;
	private final ReconciliationCasePort reconciliationCasePort;
	private final MeterRegistry meterRegistry;

	PaymentIntentReconciliationCheckService(
		PaymentIntentStore paymentIntentStore,
		AuthorizationAttemptEvidencePort attemptEvidencePort,
		PspAuthorizationPort pspAuthorizationPort,
		AuthorizationReconciliationPort authorizationReconciliationPort,
		ReconciliationCasePort reconciliationCasePort,
		MeterRegistry meterRegistry
	) {
		this.paymentIntentStore = paymentIntentStore;
		this.attemptEvidencePort = attemptEvidencePort;
		this.pspAuthorizationPort = pspAuthorizationPort;
		this.authorizationReconciliationPort = authorizationReconciliationPort;
		this.reconciliationCasePort = reconciliationCasePort;
		this.meterRegistry = meterRegistry;
	}

	public ReconciliationCheckOutcome check(PaymentIntentId paymentIntentId) {
		PaymentIntent paymentIntent = paymentIntentStore.findById(paymentIntentId)
			.orElseThrow(() -> new IllegalArgumentException("no payment intent exists for " + paymentIntentId));

		Optional<PspAttemptId> attemptId = attemptEvidencePort.lastAttemptId(paymentIntentId);
		if (attemptId.isEmpty()) {
			return new ReconciliationCheckOutcome.NoEvidence();
		}

		PspAuthorizationResult providerResult = pspAuthorizationPort.checkStatus(attemptId.get());
		PaymentIntentState localState = paymentIntent.state();

		if (UNCERTAIN_STATES.contains(localState)) {
			PaymentIntent resolved = authorizationReconciliationPort.applyReconciledResult(paymentIntentId, providerResult);
			Counter.builder("sentinel.payments.authorization.recovery")
				.description("Uncertain authorizations resolved through reconciliation, by resulting state")
				.tag("outcome", resolved.state().name())
				.register(meterRegistry)
				.increment();
			return new ReconciliationCheckOutcome.AutoResolved(resolved.state());
		}

		if (isConsistent(localState, providerResult)) {
			return new ReconciliationCheckOutcome.AlreadyConsistent(localState);
		}

		String localEvidence = localState.name();
		String providerEvidence = describe(providerResult);
		String fingerprint = ReconciliationCase.fingerprint(
			paymentIntentId, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE, localEvidence, providerEvidence
		);
		ReconciliationSeverity severity = FINANCIAL_STATES.contains(localState) ? ReconciliationSeverity.HIGH : ReconciliationSeverity.LOW;
		ReconciliationCase candidate = new ReconciliationCase(
			ReconciliationCaseId.generate(), paymentIntentId, fingerprint,
			ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE, severity, ReconciliationCaseStatus.OPEN,
			localEvidence, providerEvidence, Instant.now(), null
		);
		ReconciliationOpenOutcome outcome = reconciliationCasePort.open(candidate);
		if (outcome instanceof ReconciliationOpenOutcome.Opened) {
			Counter.builder("sentinel.reconciliation.cases.opened")
				.description("Reconciliation cases newly opened, by severity")
				.tag("severity", severity.name())
				.register(meterRegistry)
				.increment();
		}
		return new ReconciliationCheckOutcome.MismatchDetected(outcome);
	}

	private static boolean isConsistent(PaymentIntentState localState, PspAuthorizationResult providerResult) {
		if (AUTHORIZED_FAMILY.contains(localState)) {
			return providerResult instanceof PspAuthorizationResult.Approved;
		}
		if (localState == PaymentIntentState.DECLINED) {
			return providerResult instanceof PspAuthorizationResult.Declined;
		}
		if (localState == PaymentIntentState.FAILED) {
			return providerResult instanceof PspAuthorizationResult.PermanentFailure;
		}
		return true;
	}

	private static String describe(PspAuthorizationResult result) {
		if (result instanceof PspAuthorizationResult.Approved approved) {
			return "APPROVED:" + approved.reference().value();
		}
		if (result instanceof PspAuthorizationResult.Declined declined) {
			return "DECLINED:" + declined.reasonCode();
		}
		if (result instanceof PspAuthorizationResult.Unknown) {
			return "UNKNOWN";
		}
		if (result instanceof PspAuthorizationResult.RetryableFailure retryable) {
			return "RETRYABLE_FAILURE:" + retryable.detail();
		}
		if (result instanceof PspAuthorizationResult.PermanentFailure permanent) {
			return "PERMANENT_FAILURE:" + permanent.detail();
		}
		throw new IllegalArgumentException("unsupported PSP authorization result: " + result);
	}
}
