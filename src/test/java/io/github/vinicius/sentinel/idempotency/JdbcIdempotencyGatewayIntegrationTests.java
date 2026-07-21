package io.github.vinicius.sentinel.idempotency;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JdbcIdempotencyGatewayIntegrationTests {

	private static final String OPERATION = "payment-intent.create";

	@Autowired
	private IdempotencyGateway idempotencyGateway;

	@Test
	void acquiresAFreshKeyExactlyOnce() {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();

		IdempotencyAcquisition acquisition = idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");

		assertThat(acquisition).isInstanceOf(IdempotencyAcquisition.Acquired.class);
	}

	@Test
	void reportsInProgressForTheSameKeyAndHashWhileNotYetTerminal() {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();

		idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");
		IdempotencyAcquisition second = idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");

		assertThat(second).isInstanceOf(IdempotencyAcquisition.InProgress.class);
	}

	@Test
	void reportsKeyConflictForTheSameKeyWithADifferentHash() {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();

		idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");
		IdempotencyAcquisition second = idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-b");

		assertThat(second).isInstanceOf(IdempotencyAcquisition.KeyConflict.class);
	}

	@Test
	void replaysTheStoredResponseAfterCompletion() {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();
		StoredResponse response = new StoredResponse(201, "application/json", "{\"id\":\"abc\"}", "/api/v1/payment-intents/abc");

		idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");
		idempotencyGateway.complete(merchantId, OPERATION, key, response);
		IdempotencyAcquisition replay = idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");

		assertThat(replay).isInstanceOf(IdempotencyAcquisition.Replayed.class);
		assertThat(((IdempotencyAcquisition.Replayed) replay).response()).isEqualTo(response);
	}

	@Test
	void replaysTheStoredResponseAfterATerminalFailure() {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();
		StoredResponse response = new StoredResponse(422, "application/problem+json", "{\"code\":\"UNSUPPORTED_CURRENCY\"}", null);

		idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");
		idempotencyGateway.failTerminal(merchantId, OPERATION, key, response);
		IdempotencyAcquisition replay = idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");

		assertThat(replay).isInstanceOf(IdempotencyAcquisition.Replayed.class);
		assertThat(((IdempotencyAcquisition.Replayed) replay).response()).isEqualTo(response);
	}

	@Test
	void reportsRecoveryRequiredForTheSameKeyAndHashAfterMarkingItSo() {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();

		idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");
		idempotencyGateway.markRecoveryRequired(merchantId, OPERATION, key, "payment-intent-123");
		IdempotencyAcquisition second = idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");

		assertThat(second).isInstanceOf(IdempotencyAcquisition.RecoveryRequired.class);
		assertThat(((IdempotencyAcquisition.RecoveryRequired) second).resourceId()).isEqualTo("payment-intent-123");
	}

	@Test
	void completingFromRecoveryRequiredReplaysTheStoredResponseAfterwards() {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();
		StoredResponse response = new StoredResponse(200, "application/json", "{\"state\":\"AUTHORIZED\"}", null);

		idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");
		idempotencyGateway.markRecoveryRequired(merchantId, OPERATION, key, "payment-intent-123");
		idempotencyGateway.complete(merchantId, OPERATION, key, response);
		IdempotencyAcquisition replay = idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");

		assertThat(replay).isInstanceOf(IdempotencyAcquisition.Replayed.class);
		assertThat(((IdempotencyAcquisition.Replayed) replay).response()).isEqualTo(response);
	}

	@Test
	void concurrentAcquisitionForTheSameKeyHasExactlyOneOwner() throws Exception {
		UUID merchantId = UUID.randomUUID();
		IdempotencyKey key = newKey();
		int racers = 8;
		ExecutorService executor = Executors.newFixedThreadPool(racers);
		CyclicBarrier barrier = new CyclicBarrier(racers);

		try {
			List<Callable<IdempotencyAcquisition>> tasks = List.of(
				() -> raceAcquire(merchantId, key, barrier),
				() -> raceAcquire(merchantId, key, barrier),
				() -> raceAcquire(merchantId, key, barrier),
				() -> raceAcquire(merchantId, key, barrier),
				() -> raceAcquire(merchantId, key, barrier),
				() -> raceAcquire(merchantId, key, barrier),
				() -> raceAcquire(merchantId, key, barrier),
				() -> raceAcquire(merchantId, key, barrier)
			);

			List<Future<IdempotencyAcquisition>> futures = executor.invokeAll(tasks);

			long acquiredCount = 0;
			long inProgressCount = 0;
			for (Future<IdempotencyAcquisition> future : futures) {
				IdempotencyAcquisition outcome = future.get();
				if (outcome instanceof IdempotencyAcquisition.Acquired) {
					acquiredCount++;
				} else if (outcome instanceof IdempotencyAcquisition.InProgress) {
					inProgressCount++;
				}
			}

			assertThat(acquiredCount).isEqualTo(1);
			assertThat(inProgressCount).isEqualTo(racers - 1);
		} finally {
			executor.shutdownNow();
		}
	}

	private IdempotencyAcquisition raceAcquire(UUID merchantId, IdempotencyKey key, CyclicBarrier barrier) throws Exception {
		barrier.await();
		return idempotencyGateway.acquire(merchantId, OPERATION, key, "hash-a");
	}

	private static IdempotencyKey newKey() {
		return new IdempotencyKey(UUID.randomUUID().toString());
	}
}
