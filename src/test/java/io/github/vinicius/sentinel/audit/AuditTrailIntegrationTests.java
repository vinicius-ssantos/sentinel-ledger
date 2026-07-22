package io.github.vinicius.sentinel.audit;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditTrailIntegrationTests {

	@Autowired
	private AuditGateway auditGateway;

	@Autowired
	private AuditTrailPort auditTrailPort;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void recordsAnEventAndReadsItBackWithItsMetadataIntact() {
		String resourceId = UUID.randomUUID().toString();
		AuditEvent event = AuditEvent.record(
			AuditActor.merchant(UUID.randomUUID().toString()),
			"payment-intent.capture",
			"payment_intent",
			resourceId,
			UUID.randomUUID().toString(),
			Map.of("state", "CAPTURED", "capturedAmountInMinorUnits", "5000"),
			Instant.now()
		);

		auditGateway.record(event);

		List<AuditEvent> events = auditTrailPort.findByResource("payment_intent", resourceId);
		assertThat(events).hasSize(1);
		assertThat(events.getFirst().action()).isEqualTo("payment-intent.capture");
		assertThat(events.getFirst().actor()).isEqualTo(event.actor());
		assertThat(events.getFirst().metadata()).isEqualTo(Map.of("state", "CAPTURED", "capturedAmountInMinorUnits", "5000"));
		assertThat(events.getFirst().correlationId()).isEqualTo(event.correlationId());
	}

	@Test
	void ordersEventsForAResourceByOccurredAtThenIdAscending() {
		String resourceId = UUID.randomUUID().toString();
		Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		AuditEvent first = eventAt(resourceId, base);
		AuditEvent second = eventAt(resourceId, base.plusMillis(10));
		AuditEvent third = eventAt(resourceId, base.plusMillis(20));

		auditGateway.record(second);
		auditGateway.record(third);
		auditGateway.record(first);

		List<AuditEvent> events = auditTrailPort.findByResource("payment_intent", resourceId);
		assertThat(events).extracting(AuditEvent::id).containsExactly(first.id(), second.id(), third.id());
	}

	@Test
	void auditEventsCannotBeUpdatedEvenBelowTheApplicationLayer() {
		String resourceId = UUID.randomUUID().toString();
		AuditEvent event = eventAt(resourceId, Instant.now());
		auditGateway.record(event);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"update audit_events set action = 'tampered' where id = ?", event.id().value()
		)).isInstanceOf(Exception.class);
	}

	@Test
	void auditEventsCannotBeDeletedEvenBelowTheApplicationLayer() {
		String resourceId = UUID.randomUUID().toString();
		AuditEvent event = eventAt(resourceId, Instant.now());
		auditGateway.record(event);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"delete from audit_events where id = ?", event.id().value()
		)).isInstanceOf(Exception.class);
	}

	private static AuditEvent eventAt(String resourceId, Instant occurredAt) {
		return AuditEvent.record(
			AuditActor.merchant(UUID.randomUUID().toString()),
			"payment-intent.capture",
			"payment_intent",
			resourceId,
			UUID.randomUUID().toString(),
			Map.of(),
			occurredAt
		);
	}
}
