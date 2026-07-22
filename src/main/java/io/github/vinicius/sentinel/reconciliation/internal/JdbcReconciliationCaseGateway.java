package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.payments.PaymentIntentId;
import io.github.vinicius.sentinel.reconciliation.OperatorId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCase;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseId;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCasePort;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseStatus;
import io.github.vinicius.sentinel.reconciliation.ReconciliationMismatchType;
import io.github.vinicius.sentinel.reconciliation.ReconciliationOpenOutcome;
import io.github.vinicius.sentinel.reconciliation.ReconciliationResolution;
import io.github.vinicius.sentinel.reconciliation.ReconciliationResolutionAction;
import io.github.vinicius.sentinel.reconciliation.ReconciliationSeverity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcReconciliationCaseGateway implements ReconciliationCasePort {

	private static final List<String> OPEN_STATUSES = List.of(
		ReconciliationCaseStatus.OPEN.name(), ReconciliationCaseStatus.INVESTIGATING.name()
	);

	private final JdbcClient jdbcClient;

	JdbcReconciliationCaseGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@Transactional
	public ReconciliationOpenOutcome open(ReconciliationCase candidate) {
		int inserted = jdbcClient.sql("""
				INSERT INTO reconciliation_cases (
					id, payment_intent_id, fingerprint, mismatch_type, severity, status,
					local_evidence, provider_evidence, detected_at
				) VALUES (
					:id, :paymentIntentId, :fingerprint, :mismatchType, :severity, :status,
					:localEvidence, :providerEvidence, :detectedAt
				)
				ON CONFLICT (fingerprint) WHERE status IN ('OPEN', 'INVESTIGATING') DO NOTHING
				""")
			.param("id", candidate.id().value())
			.param("paymentIntentId", candidate.paymentIntentId().value())
			.param("fingerprint", candidate.fingerprint())
			.param("mismatchType", candidate.mismatchType().name())
			.param("severity", candidate.severity().name())
			.param("status", candidate.status().name())
			.param("localEvidence", candidate.localEvidence())
			.param("providerEvidence", candidate.providerEvidence())
			.param("detectedAt", Timestamp.from(candidate.detectedAt()))
			.update();

		if (inserted == 1) {
			return new ReconciliationOpenOutcome.Opened(candidate);
		}

		ReconciliationCase existing = jdbcClient.sql("""
				SELECT * FROM reconciliation_cases WHERE fingerprint = :fingerprint AND status IN ('OPEN', 'INVESTIGATING')
				""")
			.param("fingerprint", candidate.fingerprint())
			.query(JdbcReconciliationCaseGateway::toCase)
			.single();
		return new ReconciliationOpenOutcome.AlreadyOpen(existing);
	}

	@Override
	@Transactional
	public ReconciliationCase resolve(ReconciliationCaseId id, ReconciliationResolution resolution) {
		ReconciliationCaseStatus targetStatus = resolution.action() == ReconciliationResolutionAction.IGNORE
			? ReconciliationCaseStatus.IGNORED_WITH_REASON
			: ReconciliationCaseStatus.RESOLVED;

		int updated = jdbcClient.sql("""
				UPDATE reconciliation_cases SET
					status = :status,
					resolved_by_operator_id = :operatorId,
					resolution_reason = :reason,
					resolution_action = :action,
					compensating_transaction_reference = :compensatingReference,
					resolved_at = :resolvedAt
				WHERE id = :id AND status IN ('OPEN', 'INVESTIGATING')
				""")
			.param("status", targetStatus.name())
			.param("operatorId", resolution.actor().value())
			.param("reason", resolution.reason())
			.param("action", resolution.action().name())
			.param("compensatingReference", resolution.compensatingTransactionReference())
			.param("resolvedAt", Timestamp.from(resolution.resolvedAt()))
			.param("id", id.value())
			.update();

		if (updated == 0) {
			throw new IllegalStateException("no open reconciliation case " + id + " to resolve");
		}

		return findById(id).orElseThrow(() -> new IllegalStateException("resolved case disappeared: " + id));
	}

	@Override
	public Optional<ReconciliationCase> findById(ReconciliationCaseId id) {
		return jdbcClient.sql("SELECT * FROM reconciliation_cases WHERE id = :id")
			.param("id", id.value())
			.query(JdbcReconciliationCaseGateway::toCase)
			.optional();
	}

	@Override
	public List<ReconciliationCase> findAll(ReconciliationCaseStatus statusFilter) {
		if (statusFilter == null) {
			return jdbcClient.sql("SELECT * FROM reconciliation_cases ORDER BY detected_at DESC")
				.query(JdbcReconciliationCaseGateway::toCase)
				.list();
		}
		return jdbcClient.sql("SELECT * FROM reconciliation_cases WHERE status = :status ORDER BY detected_at DESC")
			.param("status", statusFilter.name())
			.query(JdbcReconciliationCaseGateway::toCase)
			.list();
	}

	private static ReconciliationCase toCase(ResultSet rs, int rowNum) throws SQLException {
		ReconciliationCaseStatus status = ReconciliationCaseStatus.valueOf(rs.getString("status"));
		ReconciliationResolution resolution = null;
		String resolutionAction = rs.getString("resolution_action");
		if (resolutionAction != null) {
			resolution = new ReconciliationResolution(
				new OperatorId(rs.getObject("resolved_by_operator_id", UUID.class)),
				rs.getString("resolution_reason"),
				ReconciliationResolutionAction.valueOf(resolutionAction),
				rs.getString("compensating_transaction_reference"),
				rs.getTimestamp("resolved_at").toInstant()
			);
		}
		return new ReconciliationCase(
			new ReconciliationCaseId(rs.getObject("id", UUID.class)),
			new PaymentIntentId(rs.getObject("payment_intent_id", UUID.class)),
			rs.getString("fingerprint"),
			ReconciliationMismatchType.valueOf(rs.getString("mismatch_type")),
			ReconciliationSeverity.valueOf(rs.getString("severity")),
			status,
			rs.getString("local_evidence"),
			rs.getString("provider_evidence"),
			rs.getTimestamp("detected_at").toInstant(),
			resolution
		);
	}
}
