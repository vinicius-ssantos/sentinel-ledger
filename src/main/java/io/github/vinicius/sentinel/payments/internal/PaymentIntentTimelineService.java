package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditTrailPort;
import io.github.vinicius.sentinel.ledger.LedgerTransaction;
import io.github.vinicius.sentinel.ledger.LedgerPostingPort;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Correlates the four evidence sources docs/INVARIANTS.md's {@code Demonstration} proof level asks a timeline to
 * connect: payment transitions and command actors (audit events), provider results (resolved PSP evidence), and
 * ledger postings. Read-only; never opens a write transaction.
 */
@Service
public class PaymentIntentTimelineService {

	private static final String RESOURCE_TYPE = "payment_intent";

	private final AuditTrailPort auditTrailPort;
	private final AuthorizationAttemptStore attemptStore;
	private final LedgerPostingPort ledgerPostingPort;

	PaymentIntentTimelineService(
		AuditTrailPort auditTrailPort, AuthorizationAttemptStore attemptStore, LedgerPostingPort ledgerPostingPort
	) {
		this.auditTrailPort = auditTrailPort;
		this.attemptStore = attemptStore;
		this.ledgerPostingPort = ledgerPostingPort;
	}

	public List<TimelineEntry> timeline(PaymentIntentId paymentIntentId) {
		String resourceId = paymentIntentId.value().toString();
		List<TimelineEntry> entries = new ArrayList<>();

		for (AuditEvent event : auditTrailPort.findByResource(RESOURCE_TYPE, resourceId)) {
			entries.add(new TimelineEntry(TimelineEntryType.AUDIT_EVENT, event.action(), event.occurredAt(), event.metadata()));
		}

		for (ResolvedAuthorizationAttempt attempt : attemptStore.findResolutions(paymentIntentId)) {
			Map<String, String> details = new LinkedHashMap<>();
			details.put("outcome", attempt.outcome());
			if (attempt.providerReference() != null) {
				details.put("providerReference", attempt.providerReference());
			}
			if (attempt.reasonCode() != null) {
				details.put("reasonCode", attempt.reasonCode());
			}
			entries.add(new TimelineEntry(
				TimelineEntryType.PROVIDER_RESULT, "authorization." + attempt.outcome().toLowerCase(), attempt.occurredAt(), details
			));
		}

		for (String prefix : List.of("capture:" + resourceId + ":", "refund:" + resourceId + ":")) {
			for (LedgerTransaction transaction : ledgerPostingPort.findByBusinessEffectReferencePrefix(prefix)) {
				Map<String, String> details = new LinkedHashMap<>();
				details.put("businessEffectReference", transaction.businessEffectReference());
				details.put("currency", transaction.currency().code());
				String label = transaction.businessEffectReference().startsWith("capture:")
					? "ledger.capture" : "ledger.refund";
				entries.add(new TimelineEntry(TimelineEntryType.LEDGER_TRANSACTION, label, transaction.postedAt(), details));
			}
		}

		entries.sort(Comparator.comparing(TimelineEntry::occurredAt));
		return List.copyOf(entries);
	}
}
