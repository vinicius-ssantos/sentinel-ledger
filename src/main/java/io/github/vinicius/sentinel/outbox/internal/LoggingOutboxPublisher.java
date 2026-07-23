package io.github.vinicius.sentinel.outbox.internal;

import io.github.vinicius.sentinel.outbox.OutboxPublisherPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder publisher until a real broker adapter exists (issue #22). It only records that publication would
 * occur, logging identifiers and event type — never the payload, which may carry business amounts. Because the
 * claim/publish/complete pipeline treats this call exactly like a real external delivery, replacing it with a
 * RabbitMQ adapter later requires no change to the dispatch worker.
 */
@Component
class LoggingOutboxPublisher implements OutboxPublisherPort {

	private static final Logger log = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

	@Override
	public void publish(OutboxRecord event) {
		log.info(
			"outbox event published (logging placeholder): id={} aggregateType={} aggregateId={} eventType={}",
			event.id().value(), event.aggregateType(), event.aggregateId(), event.eventType()
		);
	}
}
