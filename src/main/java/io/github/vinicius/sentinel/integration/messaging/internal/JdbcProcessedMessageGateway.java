package io.github.vinicius.sentinel.integration.messaging.internal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * The consumer's inbox: {@code messageId} is the outbox event id the publisher set as the AMQP message id, so a
 * redelivered or duplicated message can never be recorded as processed twice.
 */
@Repository
class JdbcProcessedMessageGateway {

	private final JdbcClient jdbcClient;

	JdbcProcessedMessageGateway(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	/**
	 * @return {@code true} the first time {@code messageId} is seen, {@code false} on every redelivery.
	 */
	@Transactional
	boolean markProcessed(UUID messageId) {
		int inserted = jdbcClient.sql("""
				INSERT INTO messaging_inbox_processed_messages (message_id, processed_at)
				VALUES (:messageId, :processedAt)
				ON CONFLICT (message_id) DO NOTHING
				""")
			.param("messageId", messageId)
			.param("processedAt", Timestamp.from(Instant.now()))
			.update();
		return inserted == 1;
	}
}
