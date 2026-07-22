package io.github.vinicius.sentinel.audit.internal;

import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditGateway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;

@Repository
class JdbcAuditGateway implements AuditGateway {

	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper;

	JdbcAuditGateway(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional
	public void record(AuditEvent event) {
		jdbcClient.sql("""
				INSERT INTO audit_events (
					id, actor_type, actor_id, action, resource_type, resource_id, correlation_id, reason, metadata, occurred_at
				) VALUES (
					:id, :actorType, :actorId, :action, :resourceType, :resourceId, :correlationId, :reason, :metadata, :occurredAt
				)
				""")
			.param("id", event.id().value())
			.param("actorType", event.actor().type().name())
			.param("actorId", event.actor().id())
			.param("action", event.action())
			.param("resourceType", event.resourceType())
			.param("resourceId", event.resourceId())
			.param("correlationId", event.correlationId())
			.param("reason", event.reason())
			.param("metadata", objectMapper.writeValueAsString(event.metadata()))
			.param("occurredAt", Timestamp.from(event.occurredAt()))
			.update();
	}
}
