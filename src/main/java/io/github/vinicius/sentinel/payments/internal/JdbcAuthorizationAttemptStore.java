package io.github.vinicius.sentinel.payments.internal;

import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.payments.PspAttemptId;
import io.github.vinicius.sentinel.payments.PspAuthorizationResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAuthorizationAttemptStore implements AuthorizationAttemptStore {

	private final JdbcClient jdbcClient;

	JdbcAuthorizationAttemptStore(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public void recordPendingAttempt(PaymentIntentId paymentIntentId, PspAttemptId attemptId) {
		jdbcClient.sql("UPDATE payment_intents SET psp_attempt_id = :attemptId WHERE id = :id")
			.param("attemptId", attemptId.value())
			.param("id", paymentIntentId.value())
			.update();
	}

	@Override
	public Optional<PspAttemptId> findPendingAttempt(PaymentIntentId paymentIntentId) {
		return jdbcClient.sql("SELECT psp_attempt_id FROM payment_intents WHERE id = :id")
			.param("id", paymentIntentId.value())
			.query((rs, rowNum) -> new NullableUuid((UUID) rs.getObject("psp_attempt_id")))
			.optional()
			.map(NullableUuid::value)
			.filter(Objects::nonNull)
			.map(PspAttemptId::new);
	}

	@Override
	public void recordResolution(
		PaymentIntentId paymentIntentId, PspAttemptId attemptId, PspAuthorizationResult result, Instant occurredAt
	) {
		Evidence evidence = toEvidence(result);
		jdbcClient.sql("""
				INSERT INTO payment_intent_psp_attempts (
					id, payment_intent_id, psp_attempt_id, outcome, provider_reference, reason_code, occurred_at
				) VALUES (
					:id, :paymentIntentId, :attemptId, :outcome, :providerReference, :reasonCode, :occurredAt
				)
				""")
			.param("id", UUID.randomUUID())
			.param("paymentIntentId", paymentIntentId.value())
			.param("attemptId", attemptId.value())
			.param("outcome", evidence.outcome())
			.param("providerReference", evidence.providerReference())
			.param("reasonCode", evidence.reasonCode())
			.param("occurredAt", Timestamp.from(occurredAt))
			.update();
	}

	@Override
	public List<ResolvedAuthorizationAttempt> findResolutions(PaymentIntentId paymentIntentId) {
		return jdbcClient.sql("""
				SELECT outcome, provider_reference, reason_code, occurred_at
				FROM payment_intent_psp_attempts
				WHERE payment_intent_id = :paymentIntentId
				ORDER BY occurred_at ASC, id ASC
				""")
			.param("paymentIntentId", paymentIntentId.value())
			.query((rs, rowNum) -> new ResolvedAuthorizationAttempt(
				rs.getString("outcome"),
				rs.getString("provider_reference"),
				rs.getString("reason_code"),
				rs.getTimestamp("occurred_at").toInstant()
			))
			.list();
	}

	private static Evidence toEvidence(PspAuthorizationResult result) {
		if (result instanceof PspAuthorizationResult.Approved approved) {
			return new Evidence("APPROVED", approved.reference().value(), null);
		}
		if (result instanceof PspAuthorizationResult.Declined declined) {
			return new Evidence("DECLINED", null, declined.reasonCode());
		}
		if (result instanceof PspAuthorizationResult.Unknown) {
			return new Evidence("UNKNOWN", null, null);
		}
		if (result instanceof PspAuthorizationResult.RetryableFailure retryable) {
			return new Evidence("RETRYABLE_FAILURE", null, retryable.detail());
		}
		if (result instanceof PspAuthorizationResult.PermanentFailure permanent) {
			return new Evidence("PERMANENT_FAILURE", null, permanent.detail());
		}
		throw new IllegalArgumentException("unsupported PSP authorization result: " + result);
	}

	private record NullableUuid(UUID value) {}

	private record Evidence(String outcome, String providerReference, String reasonCode) {}
}
