package io.github.vinicius.sentinel.webhooks.internal;

import io.github.vinicius.sentinel.webhooks.WebhookDeliveryId;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryQueryPort;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryRecord;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryRequest;
import io.github.vinicius.sentinel.webhooks.WebhookDeliveryStatus;
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
class JdbcWebhookDeliveryGateway implements WebhookDeliveryQueryPort {

	private final JdbcClient jdbcClient;

	JdbcWebhookDeliveryGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	/**
	 * First call for a given id inserts the {@code PENDING} row; every later call (a retried delivery, same id)
	 * is a no-op here, since the row and its identity already exist.
	 */
	@Transactional
	void ensureRecorded(WebhookDeliveryRequest request) {
		jdbcClient.sql("""
				INSERT INTO webhook_deliveries (id, aggregate_type, aggregate_id, event_type, status, attempt_count, created_at)
				VALUES (:id, :aggregateType, :aggregateId, :eventType, 'PENDING', 0, :createdAt)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", request.id().value())
			.param("aggregateType", request.aggregateType())
			.param("aggregateId", request.aggregateId())
			.param("eventType", request.eventType())
			.param("createdAt", Timestamp.from(Instant.now()))
			.update();
	}

	@Transactional
	void recordAttempt(WebhookDeliveryId id, boolean success, String error) {
		if (success) {
			jdbcClient.sql("""
					UPDATE webhook_deliveries
					SET attempt_count = attempt_count + 1, status = 'DELIVERED', delivered_at = :now, last_error = NULL
					WHERE id = :id
					""")
				.param("now", Timestamp.from(Instant.now()))
				.param("id", id.value())
				.update();
		} else {
			jdbcClient.sql("""
					UPDATE webhook_deliveries SET attempt_count = attempt_count + 1, last_error = :error
					WHERE id = :id
					""")
				.param("error", error)
				.param("id", id.value())
				.update();
		}
	}

	@Transactional
	void markExhausted(WebhookDeliveryId id, String reason) {
		jdbcClient.sql("""
				UPDATE webhook_deliveries SET status = 'FAILED', last_error = :reason
				WHERE id = :id AND status = 'PENDING'
				""")
			.param("reason", reason)
			.param("id", id.value())
			.update();
	}

	@Override
	public boolean isDelivered(WebhookDeliveryId id) {
		Integer count = jdbcClient.sql("SELECT count(*) FROM webhook_deliveries WHERE id = :id AND status = 'DELIVERED'")
			.param("id", id.value())
			.query(Integer.class)
			.single();
		return count != null && count > 0;
	}

	@Override
	public List<WebhookDeliveryRecord> findByAggregate(String aggregateType, String aggregateId) {
		return jdbcClient.sql("""
				SELECT id, aggregate_type, aggregate_id, event_type, status, attempt_count, last_error, created_at, delivered_at
				FROM webhook_deliveries
				WHERE aggregate_type = :aggregateType AND aggregate_id = :aggregateId
				ORDER BY created_at ASC
				""")
			.param("aggregateType", aggregateType)
			.param("aggregateId", aggregateId)
			.query(JdbcWebhookDeliveryGateway::toRecord)
			.list();
	}

	private static WebhookDeliveryRecord toRecord(ResultSet rs, int rowNum) throws SQLException {
		Timestamp deliveredAt = rs.getTimestamp("delivered_at");
		return new WebhookDeliveryRecord(
			new WebhookDeliveryId(rs.getObject("id", UUID.class)),
			rs.getString("aggregate_type"),
			rs.getString("aggregate_id"),
			rs.getString("event_type"),
			WebhookDeliveryStatus.valueOf(rs.getString("status")),
			rs.getInt("attempt_count"),
			rs.getString("last_error"),
			rs.getTimestamp("created_at").toInstant(),
			deliveredAt == null ? null : deliveredAt.toInstant()
		);
	}
}
