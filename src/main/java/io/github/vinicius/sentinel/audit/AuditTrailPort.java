package io.github.vinicius.sentinel.audit;

import java.util.List;

public interface AuditTrailPort {

	/**
	 * Every recorded event for one resource, ordered by {@code occurredAt} then {@code id} ascending — a stable
	 * order that never reorders or skips entries as new events append. Cursor-paginated HTTP delivery of this
	 * order belongs to the timeline API that consumes it, not to this port.
	 */
	List<AuditEvent> findByResource(String resourceType, String resourceId);
}
