package io.github.vinicius.sentinel.outbox;

/**
 * {@code PENDING} -&gt; {@code CLAIMED} -&gt; {@code PUBLISHED}, or {@code CLAIMED} -&gt; {@code PENDING} on a retryable
 * publish failure, or {@code CLAIMED} -&gt; {@code FAILED} once the retry budget is exhausted. A worker that crashes
 * while {@code CLAIMED} is recovered by the stale-claim sweep, which resets the record to {@code PENDING}.
 */
public enum OutboxEventStatus {
	PENDING,
	CLAIMED,
	PUBLISHED,
	FAILED
}
