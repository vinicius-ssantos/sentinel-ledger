package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.audit.AuditActor;
import io.github.vinicius.sentinel.audit.AuditActorType;
import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditGateway;
import io.github.vinicius.sentinel.ledger.AccountId;
import io.github.vinicius.sentinel.ledger.EntryDirection;
import io.github.vinicius.sentinel.ledger.LedgerEntry;
import io.github.vinicius.sentinel.ledger.LedgerPostingPort;
import io.github.vinicius.sentinel.ledger.LedgerTransaction;
import io.github.vinicius.sentinel.ledger.LedgerTransactionId;
import io.github.vinicius.sentinel.money.Currency;
import io.github.vinicius.sentinel.money.Money;
import io.github.vinicius.sentinel.payments.PaymentIntent;
import io.github.vinicius.sentinel.payments.PaymentIntentStore;
import io.github.vinicius.sentinel.reconciliation.OperatorId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCase;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCasePort;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseStatus;
import io.github.vinicius.sentinel.reconciliation.ReconciliationResolution;
import io.github.vinicius.sentinel.reconciliation.ReconciliationResolutionAction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resolves an open {@link ReconciliationCase}. The original evidence and detection time are never touched, only
 * appended to (REC-002). {@link ReconciliationResolutionAction#COMPENSATE} posts one new compensating ledger
 * transaction reversing the payment's current net captured-minus-refunded amount — the original capture/refund
 * entries are never edited, matching LED-002.
 */
@Service
public class ReconciliationResolutionService {

	private static final String RESOLVE_OPERATION = "reconciliation.resolve";

	private final ReconciliationCasePort reconciliationCasePort;
	private final PaymentIntentStore paymentIntentStore;
	private final LedgerPostingPort ledgerPostingPort;
	private final AuditGateway auditGateway;

	ReconciliationResolutionService(
		ReconciliationCasePort reconciliationCasePort,
		PaymentIntentStore paymentIntentStore,
		LedgerPostingPort ledgerPostingPort,
		AuditGateway auditGateway
	) {
		this.reconciliationCasePort = reconciliationCasePort;
		this.paymentIntentStore = paymentIntentStore;
		this.ledgerPostingPort = ledgerPostingPort;
		this.auditGateway = auditGateway;
	}

	@Transactional
	public ReconciliationCase resolve(
		ReconciliationCaseId caseId, OperatorId operatorId, String reason, ReconciliationResolutionAction action
	) {
		ReconciliationCase existing = reconciliationCasePort.findById(caseId)
			.orElseThrow(() -> new IllegalArgumentException("no reconciliation case " + caseId));
		if (existing.status() != ReconciliationCaseStatus.OPEN && existing.status() != ReconciliationCaseStatus.INVESTIGATING) {
			throw new IllegalStateException("reconciliation case " + caseId + " is already " + existing.status());
		}

		String compensatingReference = action == ReconciliationResolutionAction.COMPENSATE
			? postCompensatingTransaction(existing)
			: null;

		Instant now = Instant.now();
		ReconciliationResolution resolution = new ReconciliationResolution(operatorId, reason, action, compensatingReference, now);
		ReconciliationCase resolved = reconciliationCasePort.resolve(caseId, resolution);

		auditGateway.record(AuditEvent.record(
			new AuditActor(AuditActorType.OPERATOR, operatorId.value().toString()),
			RESOLVE_OPERATION,
			"reconciliation_case",
			caseId.value().toString(),
			"reconciliation-resolution:" + caseId.value(),
			Map.of(
				"action", action.name(),
				"paymentIntentId", existing.paymentIntentId().value().toString()
			),
			now
		));

		return resolved;
	}

	private String postCompensatingTransaction(ReconciliationCase reconciliationCase) {
		PaymentIntent paymentIntent = paymentIntentStore.findById(reconciliationCase.paymentIntentId())
			.orElseThrow(() -> new IllegalStateException("payment intent disappeared during reconciliation resolution: " + reconciliationCase.paymentIntentId()));

		Money netAmount = paymentIntent.capturedAmount().subtract(paymentIntent.refundedAmount());
		if (!netAmount.isPositive()) {
			throw new IllegalStateException("no outstanding captured amount to compensate for " + reconciliationCase.paymentIntentId());
		}

		List<LedgerTransaction> captures = ledgerPostingPort.findByBusinessEffectReferencePrefix(
			"capture:" + reconciliationCase.paymentIntentId().value() + ":"
		);
		if (captures.isEmpty()) {
			throw new IllegalStateException("no capture transaction found to compensate for " + reconciliationCase.paymentIntentId());
		}
		LedgerTransaction mostRecentCapture = captures.getLast();

		// Reconciliation is not allowed to depend on the merchant module, so the merchant-payable account is read
		// directly from the original capture's own credit entry rather than reconstructed from PaymentIntent.merchantId().
		AccountId payable = mostRecentCapture.entries().stream()
			.filter(entry -> entry.direction() == EntryDirection.CREDIT)
			.map(LedgerEntry::accountId)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("capture transaction has no credit entry: " + mostRecentCapture.id()));
		AccountId receivable = AccountId.pspClearingReceivable(Currency.BRL);
		LedgerTransaction correction = LedgerTransaction.compensate(
			LedgerTransactionId.generate(),
			"reconciliation-correction:" + reconciliationCase.id().value(),
			Currency.BRL,
			List.of(
				new LedgerEntry(payable, EntryDirection.DEBIT, netAmount),
				new LedgerEntry(receivable, EntryDirection.CREDIT, netAmount)
			),
			mostRecentCapture.id(),
			Instant.now()
		);
		ledgerPostingPort.post(correction);
		return correction.businessEffectReference();
	}
}
