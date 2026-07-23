package io.github.vinicius.sentinel.outbox.internal;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.outbox.OutboxEvent;
import io.github.vinicius.sentinel.outbox.OutboxEventStatus;
import io.github.vinicius.sentinel.outbox.OutboxGateway;
import io.github.vinicius.sentinel.outbox.OutboxPublisherPort;
import io.github.vinicius.sentinel.outbox.OutboxQueryPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "sentinel.outbox.claim-stale-after=PT0S")
@Import({TestcontainersConfiguration.class, OutboxDispatchWorkerIntegrationTests.RecordingPublisherConfiguration.class})
class OutboxDispatchWorkerIntegrationTests {

	@Autowired
	private OutboxGateway outboxGateway;

	@Autowired
	private OutboxQueryPort outboxQueryPort;

	@Autowired
	private OutboxDispatchWorker worker;

	@Autowired
	private JdbcOutboxGateway repository;

	@Autowired
	private RecordingOutboxPublisher publisher;

	@Test
	void dispatchPublishesAPendingEventAndMarksItPublished() {
		publisher.published.clear();
		OutboxEvent event = enqueueOne();

		worker.dispatch();

		assertThat(publisher.published).extracting(OutboxRecord::id).contains(event.id());
		List<OutboxRecord> published = outboxQueryPort.findByStatus(OutboxEventStatus.PUBLISHED);
		assertThat(published).extracting(OutboxRecord::id).contains(event.id());
	}

	@Test
	void aFailingPublishRequeuesTheEventInsteadOfAbortingTheWorker() {
		publisher.published.clear();
		publisher.failNext.set(true);
		OutboxEvent event = enqueueOne();

		worker.dispatch();

		assertThat(publisher.published).extracting(OutboxRecord::id).doesNotContain(event.id());
		List<OutboxRecord> pending = outboxQueryPort.findByStatus(OutboxEventStatus.PENDING);
		OutboxRecord found = pending.stream().filter(r -> r.id().equals(event.id())).findFirst().orElseThrow();
		assertThat(found.attemptCount()).isEqualTo(1);
		assertThat(found.lastError()).contains("simulated failure");
	}

	@Test
	void reclaimStaleClaimsResetsAWorkerThatCrashedBetweenClaimAndComplete() {
		OutboxEvent event = enqueueOne();
		repository.claimBatch(10);

		worker.reclaimStaleClaims();

		List<OutboxRecord> pending = outboxQueryPort.findByStatus(OutboxEventStatus.PENDING);
		assertThat(pending).extracting(OutboxRecord::id).contains(event.id());
	}

	@Test
	void concurrentDispatchNeverPublishesTheSameEventTwice() throws Exception {
		publisher.published.clear();
		int eventCount = 24;
		Set<UUID> enqueuedIds = new java.util.HashSet<>();
		for (int i = 0; i < eventCount; i++) {
			enqueuedIds.add(enqueueOne().id().value());
		}

		int workerCount = 4;
		ExecutorService executor = Executors.newFixedThreadPool(workerCount);
		try {
			List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
			for (int i = 0; i < workerCount; i++) {
				futures.add(executor.submit(worker::dispatch));
			}
			for (java.util.concurrent.Future<?> future : futures) {
				future.get();
			}
		} finally {
			executor.shutdown();
		}

		List<UUID> publishedIds = publisher.published.stream()
			.map(r -> r.id().value())
			.filter(enqueuedIds::contains)
			.collect(Collectors.toList());
		assertThat(publishedIds).hasSize(eventCount);
		assertThat(Set.copyOf(publishedIds)).hasSize(eventCount);
	}

	private OutboxEvent enqueueOne() {
		OutboxEvent event = OutboxEvent.enqueue(
			"payment_intent", UUID.randomUUID().toString(), "payment.captured", "{}", Instant.now()
		);
		outboxGateway.enqueue(event);
		return event;
	}

	@TestConfiguration
	static class RecordingPublisherConfiguration {

		@Bean
		@Primary
		RecordingOutboxPublisher recordingOutboxPublisher() {
			return new RecordingOutboxPublisher();
		}
	}

	static class RecordingOutboxPublisher implements OutboxPublisherPort {

		final List<OutboxRecord> published = new CopyOnWriteArrayList<>();
		final AtomicBoolean failNext = new AtomicBoolean(false);

		@Override
		public void publish(OutboxRecord event) {
			if (failNext.compareAndSet(true, false)) {
				throw new RuntimeException("simulated failure");
			}
			published.add(event);
		}
	}
}
