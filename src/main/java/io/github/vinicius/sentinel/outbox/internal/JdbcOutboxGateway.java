package io.github.vinicius.sentinel.outbox.internal;

import io.github.vinicius.sentinel.outbox.OutboxEvent;
import io.github.vinicius.sentinel.outbox.OutboxEventId;
import io.github.vinicius.sentinel.outbox.OutboxEventStatus;
import io.github.vinicius.sentinel.outbox.OutboxGateway;
import io.github.vinicius.sentinel.outbox.OutboxQueryPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcOutboxGateway implements OutboxGateway, OutboxQueryPort {

	private final JdbcClient jdbcClient;

	JdbcOutboxGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	@Transactional
	public void enqueue(OutboxEvent event) {
		jdbcClient.sql("""
				INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, attempt_count, created_at)
				VALUES (:id, :aggregateType, :aggregateId, :eventType, :payload, 'PENDING', 0, :createdAt)
				""")
			.param("id", event.id().value())
			.param("aggregateType", event.aggregateType())
			.param("aggregateId", event.aggregateId())
			.param("eventType", event.eventType())
			.param("payload", event.payload())
			.param("createdAt", Timestamp.from(event.createdAt()))
			.update();
	}

	@Override
	public List<OutboxRecord> findByStatus(OutboxEventStatus status) {
		return jdbcClient.sql("""
				SELECT id, aggregate_type, aggregate_id, event_type, payload, status, attempt_count, last_error,
					created_at, claimed_at, published_at
				FROM outbox_events
				WHERE status = :status
				ORDER BY created_at ASC
				""")
			.param("status", status.name())
			.query(JdbcOutboxGateway::toRecord)
			.list();
	}

	/**
	 * Claims up to {@code limit} pending records for this worker and marks them {@code CLAIMED} in one local
	 * transaction, so a concurrent claimer can never observe or reclaim the same rows: {@code FOR UPDATE SKIP
	 * LOCKED} lets it skip past them instead of blocking.
	 */
	@Transactional
	List<OutboxRecord> claimBatch(int limit) {
		List<UUID> candidateIds = jdbcClient.sql("""
				SELECT id FROM outbox_events
				WHERE status = 'PENDING'
				ORDER BY created_at ASC
				LIMIT :limit
				FOR UPDATE SKIP LOCKED
				""")
			.param("limit", limit)
			.query((rs, rowNum) -> rs.getObject("id", UUID.class))
			.list();

		if (candidateIds.isEmpty()) {
			return List.of();
		}

		jdbcClient.sql("""
				UPDATE outbox_events SET status = 'CLAIMED', claimed_at = :now
				WHERE id IN (:ids)
				""")
			.param("now", Timestamp.from(Instant.now()))
			.param("ids", candidateIds)
			.update();

		return jdbcClient.sql("""
				SELECT id, aggregate_type, aggregate_id, event_type, payload, status, attempt_count, last_error,
					created_at, claimed_at, published_at
				FROM outbox_events
				WHERE id IN (:ids)
				ORDER BY created_at ASC
				""")
			.param("ids", candidateIds)
			.query(JdbcOutboxGateway::toRecord)
			.list();
	}

	@Transactional
	void markPublished(OutboxEventId id) {
		jdbcClient.sql("""
				UPDATE outbox_events SET status = 'PUBLISHED', published_at = :now, last_error = NULL
				WHERE id = :id AND status = 'CLAIMED'
				""")
			.param("now", Timestamp.from(Instant.now()))
			.param("id", id.value())
			.update();
	}

	/**
	 * Records a failed publish attempt. The record returns to {@code PENDING} for another try until
	 * {@code maxAttempts} is reached, after which it is marked {@code FAILED} and left for operator visibility
	 * instead of retried forever.
	 */
	@Transactional
	void markFailed(OutboxEventId id, String error, int maxAttempts) {
		jdbcClient.sql("""
				UPDATE outbox_events
				SET attempt_count = attempt_count + 1,
					last_error = :error,
					status = CASE WHEN attempt_count + 1 >= :maxAttempts THEN 'FAILED' ELSE 'PENDING' END
				WHERE id = :id AND status = 'CLAIMED'
				""")
			.param("error", error)
			.param("maxAttempts", maxAttempts)
			.param("id", id.value())
			.update();
	}

	/**
	 * Resets records stuck in {@code CLAIMED} past {@code staleAfter} back to {@code PENDING} — the safety net for
	 * a worker that crashed between claiming and completing.
	 */
	@Transactional
	int reclaimStale(Instant claimedBefore) {
		return jdbcClient.sql("""
				UPDATE outbox_events SET status = 'PENDING', claimed_at = NULL
				WHERE status = 'CLAIMED' AND claimed_at < :claimedBefore
				""")
			.param("claimedBefore", Timestamp.from(claimedBefore))
			.update();
	}

	private static OutboxRecord toRecord(ResultSet rs, int rowNum) throws SQLException {
		Timestamp claimedAt = rs.getTimestamp("claimed_at");
		Timestamp publishedAt = rs.getTimestamp("published_at");
		return new OutboxRecord(
			new OutboxEventId(rs.getObject("id", UUID.class)),
			rs.getString("aggregate_type"),
			rs.getString("aggregate_id"),
			rs.getString("event_type"),
			rs.getString("payload"),
			OutboxEventStatus.valueOf(rs.getString("status")),
			rs.getInt("attempt_count"),
			rs.getString("last_error"),
			rs.getTimestamp("created_at").toInstant(),
			claimedAt == null ? null : claimedAt.toInstant(),
			publishedAt == null ? null : publishedAt.toInstant()
		);
	}
}
