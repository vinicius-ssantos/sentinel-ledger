package io.github.vinicius.sentinel.audit.internal;

import io.github.vinicius.sentinel.audit.AuditActor;
import io.github.vinicius.sentinel.audit.AuditActorType;
import io.github.vinicius.sentinel.audit.AuditEvent;
import io.github.vinicius.sentinel.audit.AuditEventId;
import io.github.vinicius.sentinel.audit.AuditTrailPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class JdbcAuditTrailGateway implements AuditTrailPort {

	private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {};

	private final JdbcClient jdbcClient;
	private final ObjectMapper objectMapper;

	JdbcAuditTrailGateway(JdbcClient jdbcClient, ObjectMapper objectMapper) {
		this.jdbcClient = jdbcClient;
		this.objectMapper = objectMapper;
	}

	@Override
	public List<AuditEvent> findByResource(String resourceType, String resourceId) {
		return jdbcClient.sql("""
				SELECT id, actor_type, actor_id, action, resource_type, resource_id, correlation_id, reason, metadata, occurred_at
				FROM audit_events
				WHERE resource_type = :resourceType AND resource_id = :resourceId
				ORDER BY occurred_at ASC, id ASC
				""")
			.param("resourceType", resourceType)
			.param("resourceId", resourceId)
			.query(this::toEvent)
			.list();
	}

	private AuditEvent toEvent(ResultSet rs, int rowNum) throws SQLException {
		return new AuditEvent(
			new AuditEventId(rs.getObject("id", UUID.class)),
			new AuditActor(AuditActorType.valueOf(rs.getString("actor_type")), rs.getString("actor_id")),
			rs.getString("action"),
			rs.getString("resource_type"),
			rs.getString("resource_id"),
			rs.getString("correlation_id"),
			rs.getString("reason"),
			objectMapper.readValue(rs.getString("metadata"), METADATA_TYPE),
			rs.getTimestamp("occurred_at").toInstant()
		);
	}
}
