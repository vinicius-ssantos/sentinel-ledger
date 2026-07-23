package io.github.vinicius.sentinel.outbox;

import java.util.List;

public interface OutboxQueryPort {

	/**
	 * Every record in {@code status}, ordered by {@code createdAt} ascending — used to make stuck and failed
	 * publications operationally visible.
	 */
	List<OutboxRecord> findByStatus(OutboxEventStatus status);
}
