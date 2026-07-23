package io.github.vinicius.sentinel.outbox.internal;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.outbox.OutboxEvent;
import io.github.vinicius.sentinel.outbox.OutboxEventId;
import io.github.vinicius.sentinel.outbox.OutboxEventStatus;
import io.github.vinicius.sentinel.outbox.OutboxGateway;
import io.github.vinicius.sentinel.outbox.OutboxQueryPort;
import io.github.vinicius.sentinel.outbox.OutboxRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcOutboxGatewayIntegrationTests {

	@Autowired
	private OutboxGateway outboxGateway;

	@Autowired
	private OutboxQueryPort outboxQueryPort;

	@Autowired
	private JdbcOutboxGateway repository;

	@Test
	void enqueuedEventIsFoundByPendingStatus() {
		String aggregateId = UUID.randomUUID().toString();
		OutboxEvent event = OutboxEvent.enqueue("payment_intent", aggregateId, "payment.captured", "{\"a\":1}", Instant.now());

		outboxGateway.enqueue(event);

		List<OutboxRecord> pending = outboxQueryPort.findByStatus(OutboxEventStatus.PENDING);
		assertThat(pending).extracting(OutboxRecord::id).contains(event.id());
		OutboxRecord found = pending.stream().filter(r -> r.id().equals(event.id())).findFirst().orElseThrow();
		assertThat(found.aggregateType()).isEqualTo("payment_intent");
		assertThat(found.aggregateId()).isEqualTo(aggregateId);
		assertThat(found.eventType()).isEqualTo("payment.captured");
		assertThat(found.payload()).isEqualTo("{\"a\":1}");
		assertThat(found.attemptCount()).isZero();
		assertThat(found.claimedAt()).isNull();
		assertThat(found.publishedAt()).isNull();
	}

	@Test
	void claimBatchMarksClaimedAndExcludesClaimedRecordsFromFurtherClaims() {
		OutboxEvent event = enqueueOne();

		List<OutboxRecord> firstClaim = repository.claimBatch(10);
		assertThat(firstClaim).extracting(OutboxRecord::id).contains(event.id());
		assertThat(firstClaim.stream().filter(r -> r.id().equals(event.id())).findFirst().orElseThrow().status())
			.isEqualTo(OutboxEventStatus.CLAIMED);

		List<OutboxRecord> secondClaim = repository.claimBatch(10);
		assertThat(secondClaim).extracting(OutboxRecord::id).doesNotContain(event.id());
	}

	@Test
	void markPublishedTransitionsAClaimedRecordToPublished() {
		OutboxEvent event = enqueueOne();
		repository.claimBatch(10);

		repository.markPublished(event.id());

		List<OutboxRecord> published = outboxQueryPort.findByStatus(OutboxEventStatus.PUBLISHED);
		assertThat(published).extracting(OutboxRecord::id).contains(event.id());
		assertThat(published.stream().filter(r -> r.id().equals(event.id())).findFirst().orElseThrow().publishedAt()).isNotNull();
	}

	@Test
	void markFailedRequeuesUntilMaxAttemptsThenMarksFailed() {
		OutboxEvent event = enqueueOne();
		int maxAttempts = 3;

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			repository.claimBatch(10);
			repository.markFailed(event.id(), "boom-" + attempt, maxAttempts);
			OutboxEventStatus expected = attempt < maxAttempts ? OutboxEventStatus.PENDING : OutboxEventStatus.FAILED;
			List<OutboxRecord> matches = outboxQueryPort.findByStatus(expected);
			assertThat(matches).extracting(OutboxRecord::id).contains(event.id());
		}

		List<OutboxRecord> failed = outboxQueryPort.findByStatus(OutboxEventStatus.FAILED);
		OutboxRecord found = failed.stream().filter(r -> r.id().equals(event.id())).findFirst().orElseThrow();
		assertThat(found.attemptCount()).isEqualTo(maxAttempts);
		assertThat(found.lastError()).isEqualTo("boom-" + maxAttempts);
	}

	@Test
	void reclaimStaleResetsOldClaimedRecordsToPending() {
		OutboxEvent event = enqueueOne();
		repository.claimBatch(10);

		int reclaimed = repository.reclaimStale(Instant.now().plusSeconds(60));

		assertThat(reclaimed).isGreaterThanOrEqualTo(1);
		List<OutboxRecord> pending = outboxQueryPort.findByStatus(OutboxEventStatus.PENDING);
		assertThat(pending).extracting(OutboxRecord::id).contains(event.id());
	}

	@Test
	void reclaimStaleLeavesRecentClaimsAlone() {
		enqueueOne();
		repository.claimBatch(10);

		int reclaimed = repository.reclaimStale(Instant.now().minusSeconds(120));

		assertThat(reclaimed).isZero();
	}

	@Test
	void concurrentWorkersNeverClaimTheSameRecordTwice() throws Exception {
		int eventCount = 30;
		Set<OutboxEventId> enqueued = new HashSet<>();
		for (int i = 0; i < eventCount; i++) {
			OutboxEvent event = enqueueOne();
			enqueued.add(event.id());
		}

		int workerCount = 6;
		ExecutorService executor = Executors.newFixedThreadPool(workerCount);
		try {
			List<Callable<List<OutboxRecord>>> tasks = List.of(
				() -> repository.claimBatch(eventCount),
				() -> repository.claimBatch(eventCount),
				() -> repository.claimBatch(eventCount),
				() -> repository.claimBatch(eventCount),
				() -> repository.claimBatch(eventCount),
				() -> repository.claimBatch(eventCount)
			);
			List<Future<List<OutboxRecord>>> futures = executor.invokeAll(tasks);

			List<OutboxEventId> claimedIds = futures.stream()
				.map(f -> {
					try {
						return f.get();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				})
				.flatMap(List::stream)
				.map(OutboxRecord::id)
				.filter(enqueued::contains)
				.collect(Collectors.toList());

			assertThat(claimedIds).hasSize(eventCount);
			assertThat(new HashSet<>(claimedIds)).hasSize(eventCount);
		} finally {
			executor.shutdown();
		}
	}

	private OutboxEvent enqueueOne() {
		OutboxEvent event = OutboxEvent.enqueue(
			"payment_intent", UUID.randomUUID().toString(), "payment.captured", "{}", Instant.now()
		);
		outboxGateway.enqueue(event);
		return event;
	}
}
