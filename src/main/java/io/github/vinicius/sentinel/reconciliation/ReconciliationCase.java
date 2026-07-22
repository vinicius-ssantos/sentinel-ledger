package io.github.vinicius.sentinel.reconciliation;

import io.github.vinicius.sentinel.payments.PaymentIntentId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A detected mismatch between internal state and the simulated PSP. {@code fingerprint} deterministically
 * identifies the divergence (same payment intent, same mismatch type, same pair of local/provider evidence values)
 * so repeated detection can never open a second case for the one already open — REC-001. {@code resolution} is
 * {@code null} while {@code status} is {@code OPEN}/{@code INVESTIGATING}; once present it is never replaced,
 * preserving the original evidence, actor, and reason (REC-002).
 */
public record ReconciliationCase(
	ReconciliationCaseId id,
	PaymentIntentId paymentIntentId,
	String fingerprint,
	ReconciliationMismatchType mismatchType,
	ReconciliationSeverity severity,
	ReconciliationCaseStatus status,
	String localEvidence,
	String providerEvidence,
	Instant detectedAt,
	ReconciliationResolution resolution
) {

	public ReconciliationCase {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(paymentIntentId, "paymentIntentId must not be null");
		Objects.requireNonNull(fingerprint, "fingerprint must not be null");
		if (fingerprint.isBlank()) {
			throw new IllegalArgumentException("fingerprint must not be blank");
		}
		Objects.requireNonNull(mismatchType, "mismatchType must not be null");
		Objects.requireNonNull(severity, "severity must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(localEvidence, "localEvidence must not be null");
		Objects.requireNonNull(providerEvidence, "providerEvidence must not be null");
		Objects.requireNonNull(detectedAt, "detectedAt must not be null");
		boolean resolvedStatus = status == ReconciliationCaseStatus.RESOLVED || status == ReconciliationCaseStatus.IGNORED_WITH_REASON;
		if (resolvedStatus && resolution == null) {
			throw new IllegalArgumentException(status + " requires a resolution");
		}
		if (!resolvedStatus && resolution != null) {
			throw new IllegalArgumentException(status + " must not have a resolution yet");
		}
	}

	public static String fingerprint(PaymentIntentId paymentIntentId, ReconciliationMismatchType type, String localEvidence, String providerEvidence) {
		return paymentIntentId.value() + ":" + type + ":" + localEvidence + ":" + providerEvidence;
	}

	public Optional<ReconciliationResolution> resolutionIfPresent() {
		return Optional.ofNullable(resolution);
	}
}
