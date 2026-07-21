package io.github.vinicius.sentinel.idempotency.internal;

import io.github.vinicius.sentinel.idempotency.IdempotencyAcquisition;
import io.github.vinicius.sentinel.idempotency.IdempotencyGateway;
import io.github.vinicius.sentinel.idempotency.IdempotencyKey;
import io.github.vinicius.sentinel.idempotency.StoredResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
class JdbcIdempotencyGateway implements IdempotencyGateway {

	private final JdbcClient jdbcClient;

	JdbcIdempotencyGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public IdempotencyAcquisition acquire(UUID merchantId, String operationName, IdempotencyKey key, String requestHash) {
		Timestamp now = Timestamp.from(Instant.now());
		int inserted = jdbcClient.sql("""
				INSERT INTO idempotency_records (
					merchant_id, operation_name, idempotency_key, request_hash, state, created_at, updated_at
				) VALUES (
					:merchantId, :operationName, :idempotencyKey, :requestHash, 'IN_PROGRESS', :now, :now
				)
				ON CONFLICT (merchant_id, operation_name, idempotency_key) DO NOTHING
				""")
			.param("merchantId", merchantId)
			.param("operationName", operationName)
			.param("idempotencyKey", key.value())
			.param("requestHash", requestHash)
			.param("now", now)
			.update();

		if (inserted == 1) {
			return new IdempotencyAcquisition.Acquired();
		}

		ExistingRecord existing = jdbcClient.sql("""
				SELECT request_hash, state, response_status, response_content_type, response_body, response_location
				FROM idempotency_records
				WHERE merchant_id = :merchantId AND operation_name = :operationName AND idempotency_key = :idempotencyKey
				""")
			.param("merchantId", merchantId)
			.param("operationName", operationName)
			.param("idempotencyKey", key.value())
			.query(JdbcIdempotencyGateway::toExistingRecord)
			.single();

		if (!existing.requestHash().equals(requestHash)) {
			return new IdempotencyAcquisition.KeyConflict();
		}
		if ("IN_PROGRESS".equals(existing.state())) {
			return new IdempotencyAcquisition.InProgress();
		}
		return new IdempotencyAcquisition.Replayed(new StoredResponse(
			existing.responseStatus(), existing.responseContentType(), existing.responseBody(), existing.responseLocation()
		));
	}

	@Override
	public void complete(UUID merchantId, String operationName, IdempotencyKey key, StoredResponse response) {
		updateTerminal(merchantId, operationName, key, "COMPLETED", response);
	}

	@Override
	public void failTerminal(UUID merchantId, String operationName, IdempotencyKey key, StoredResponse response) {
		updateTerminal(merchantId, operationName, key, "FAILED_TERMINAL", response);
	}

	private void updateTerminal(UUID merchantId, String operationName, IdempotencyKey key, String state, StoredResponse response) {
		jdbcClient.sql("""
				UPDATE idempotency_records
				SET state = :state, response_status = :status, response_content_type = :contentType,
					response_body = :body, response_location = :location, updated_at = :now
				WHERE merchant_id = :merchantId AND operation_name = :operationName AND idempotency_key = :idempotencyKey
					AND state = 'IN_PROGRESS'
				""")
			.param("state", state)
			.param("status", response.status())
			.param("contentType", response.contentType())
			.param("body", response.body())
			.param("location", response.location())
			.param("now", Timestamp.from(Instant.now()))
			.param("merchantId", merchantId)
			.param("operationName", operationName)
			.param("idempotencyKey", key.value())
			.update();
	}

	private record ExistingRecord(
		String requestHash,
		String state,
		Integer responseStatus,
		String responseContentType,
		String responseBody,
		String responseLocation
	) {}

	private static ExistingRecord toExistingRecord(ResultSet rs, int rowNum) throws SQLException {
		return new ExistingRecord(
			rs.getString("request_hash"),
			rs.getString("state"),
			(Integer) rs.getObject("response_status"),
			rs.getString("response_content_type"),
			rs.getString("response_body"),
			rs.getString("response_location")
		);
	}
}
