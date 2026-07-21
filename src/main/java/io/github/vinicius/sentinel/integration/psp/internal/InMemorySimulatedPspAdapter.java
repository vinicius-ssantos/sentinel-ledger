package io.github.vinicius.sentinel.integration.psp.internal;

import io.github.vinicius.sentinel.integration.psp.SimulatedPspControls;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationPort;
import io.github.vinicius.sentinel.payments.PspAuthorizationRequest;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import io.github.vinicius.sentinel.payments.PspCallback;
import io.github.vinicius.sentinel.payments.PspProviderReference;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic in-process PSP simulator. An attempt that is never programmed through {@link SimulatedPspControls}
 * authorizes as an automatic approval, so the merchant-facing authorization flow never needs to know this control
 * surface exists.
 */
@Component
class InMemorySimulatedPspAdapter implements PspAuthorizationPort, SimulatedPspControls {

	private final Map<PspAttemptId, AttemptRecord> attempts = new ConcurrentHashMap<>();
	private final Map<PaymentIntentId, PendingProgram> pendingByPaymentIntentId = new ConcurrentHashMap<>();

	@Override
	public PspAuthorizationResult authorize(PspAuthorizationRequest request) {
		PendingProgram pending = pendingByPaymentIntentId.remove(request.paymentIntentId());
		if (pending != null) {
			AttemptRecord record = new AttemptRecord();
			record.authorizeOutcome = pending.authorizeOutcome();
			record.statusOutcome = pending.statusOutcome();
			appendHistory(record, request.attemptId(), pending.authorizeOutcome());
			if (!pending.statusOutcome().equals(pending.authorizeOutcome())) {
				appendHistory(record, request.attemptId(), pending.statusOutcome());
			}
			attempts.put(request.attemptId(), record);
			return record.authorizeOutcome;
		}
		return attempts.computeIfAbsent(request.attemptId(), InMemorySimulatedPspAdapter::defaultApprovedRecord).authorizeOutcome;
	}

	@Override
	public PspAuthorizationResult checkStatus(PspAttemptId attemptId) {
		AttemptRecord record = attempts.get(attemptId);
		return record == null ? new PspAuthorizationResult.Unknown() : record.statusOutcome;
	}

	@Override
	public void programApproval(PspAttemptId attemptId, PspProviderReference reference) {
		PspAuthorizationResult approved = new PspAuthorizationResult.Approved(reference);
		program(attemptId, approved, approved);
	}

	@Override
	public void programDecline(PspAttemptId attemptId, String reasonCode) {
		PspAuthorizationResult declined = new PspAuthorizationResult.Declined(reasonCode);
		program(attemptId, declined, declined);
	}

	@Override
	public void programTimeoutBeforeProcessing(PspAttemptId attemptId) {
		PspAuthorizationResult unknown = new PspAuthorizationResult.Unknown();
		program(attemptId, unknown, unknown);
	}

	@Override
	public void programTimeoutAfterProcessing(PspAttemptId attemptId, PspAuthorizationResult trueOutcome) {
		program(attemptId, new PspAuthorizationResult.Unknown(), trueOutcome);
	}

	@Override
	public void programRetryableFailure(PspAttemptId attemptId, String detail) {
		PspAuthorizationResult failure = new PspAuthorizationResult.RetryableFailure(detail);
		program(attemptId, failure, failure);
	}

	@Override
	public void programPermanentFailure(PspAttemptId attemptId, String detail) {
		PspAuthorizationResult failure = new PspAuthorizationResult.PermanentFailure(detail);
		program(attemptId, failure, failure);
	}

	@Override
	public void programStatusMismatch(PspAttemptId attemptId, PspAuthorizationResult reportedOutcome, PspAuthorizationResult trueOutcome) {
		program(attemptId, reportedOutcome, trueOutcome);
	}

	@Override
	public PspCallback deliverCallback(PspAttemptId attemptId) {
		List<PspCallback> history = requireRecord(attemptId).history;
		if (history.isEmpty()) {
			throw new IllegalStateException("no callback has been recorded for " + attemptId);
		}
		return history.get(history.size() - 1);
	}

	@Override
	public PspCallback deliverCallback(PspAttemptId attemptId, long sequence) {
		return requireRecord(attemptId).history.stream()
			.filter(callback -> callback.sequence() == sequence)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no callback with sequence " + sequence + " for " + attemptId));
	}

	@Override
	public void reset() {
		attempts.clear();
		pendingByPaymentIntentId.clear();
	}

	@Override
	public void programNextAttempt(
		PaymentIntentId paymentIntentId, PspAuthorizationResult authorizeOutcome, PspAuthorizationResult statusOutcome
	) {
		pendingByPaymentIntentId.put(paymentIntentId, new PendingProgram(authorizeOutcome, statusOutcome));
	}

	private AttemptRecord requireRecord(PspAttemptId attemptId) {
		AttemptRecord record = attempts.get(attemptId);
		if (record == null) {
			throw new IllegalStateException("no attempt is known for " + attemptId);
		}
		return record;
	}

	private void program(PspAttemptId attemptId, PspAuthorizationResult authorizeOutcome, PspAuthorizationResult statusOutcome) {
		AttemptRecord record = attempts.computeIfAbsent(attemptId, id -> new AttemptRecord());
		record.authorizeOutcome = authorizeOutcome;
		record.statusOutcome = statusOutcome;
		appendHistory(record, attemptId, authorizeOutcome);
		if (!statusOutcome.equals(authorizeOutcome)) {
			appendHistory(record, attemptId, statusOutcome);
		}
	}

	private static void appendHistory(AttemptRecord record, PspAttemptId attemptId, PspAuthorizationResult result) {
		long sequence = record.sequence.incrementAndGet();
		record.history.add(new PspCallback(attemptId, result, sequence, Instant.now()));
	}

	private static AttemptRecord defaultApprovedRecord(PspAttemptId attemptId) {
		AttemptRecord record = new AttemptRecord();
		PspAuthorizationResult approved = new PspAuthorizationResult.Approved(
			new PspProviderReference("sim-" + UUID.randomUUID())
		);
		record.authorizeOutcome = approved;
		record.statusOutcome = approved;
		appendHistory(record, attemptId, approved);
		return record;
	}

	private static final class AttemptRecord {
		private volatile PspAuthorizationResult authorizeOutcome;
		private volatile PspAuthorizationResult statusOutcome;
		private final List<PspCallback> history = new CopyOnWriteArrayList<>();
		private final AtomicLong sequence = new AtomicLong(0);
	}

	private record PendingProgram(PspAuthorizationResult authorizeOutcome, PspAuthorizationResult statusOutcome) {}
}
