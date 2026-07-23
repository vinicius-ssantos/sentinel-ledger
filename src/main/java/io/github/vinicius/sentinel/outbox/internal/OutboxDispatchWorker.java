package io.github.vinicius.sentinel.outbox.internal;

import io.github.vinicius.sentinel.outbox.OutboxPublisherPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Claim, publish, and complete are three separate steps, mirroring the payments module's persist-call-persist
 * discipline for external calls: {@link JdbcOutboxGateway#claimBatch} commits before {@link #dispatch()} ever calls
 * the publisher, and the publisher runs with no transaction open around it. A publish failure marks only that
 * record; it never aborts the rest of the batch.
 */
@Component
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxDispatchWorker {

	private static final Logger log = LoggerFactory.getLogger(OutboxDispatchWorker.class);

	private final JdbcOutboxGateway repository;
	private final OutboxPublisherPort publisherPort;
	private final OutboxProperties properties;
	private final MeterRegistry meterRegistry;

	OutboxDispatchWorker(
		JdbcOutboxGateway repository, OutboxPublisherPort publisherPort, OutboxProperties properties, MeterRegistry meterRegistry
	) {
		this.repository = repository;
		this.publisherPort = publisherPort;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	@Scheduled(fixedDelayString = "${sentinel.outbox.dispatch-interval:PT5S}")
	void dispatch() {
		List<OutboxRecord> claimed = repository.claimBatch(properties.batchSize());
		for (OutboxRecord record : claimed) {
			try {
				publisherPort.publish(record);
				repository.markPublished(record.id());
				publishResultCounter("SUCCESS", record.eventType()).increment();
			} catch (Exception e) {
				log.warn("outbox publish failed for event {} ({}): {}", record.id().value(), record.eventType(), e.getMessage());
				repository.markFailed(record.id(), safeMessage(e), properties.maxAttempts());
				publishResultCounter("FAILURE", record.eventType()).increment();
			}
		}
	}

	/**
	 * Tagged by outcome and the outbox event type -- both fixed, small sets (event types are the handful of
	 * business events this codebase enqueues, never a payment or merchant identifier).
	 */
	private Counter publishResultCounter(String result, String eventType) {
		return Counter.builder("sentinel.outbox.publish.result")
			.description("Outbox publish attempts, by outcome and event type")
			.tag("result", result)
			.tag("eventType", eventType)
			.register(meterRegistry);
	}

	@Scheduled(fixedDelayString = "${sentinel.outbox.reclaim-interval:PT1M}")
	void reclaimStaleClaims() {
		Instant claimedBefore = Instant.now().minus(properties.claimStaleAfter());
		int reclaimed = repository.reclaimStale(claimedBefore);
		if (reclaimed > 0) {
			log.warn("reclaimed {} stale outbox claim(s) older than {}", reclaimed, claimedBefore);
		}
	}

	private static String safeMessage(Exception e) {
		String message = e.getMessage();
		return message == null ? e.getClass().getSimpleName() : message;
	}
}
