package io.github.vinicius.sentinel.reconciliation;

import io.github.vinicius.sentinel.TestcontainersConfiguration;
import io.github.vinicius.sentinel.payments.PaymentIntentId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReconciliationCasePersistenceIntegrationTests {

	@Autowired
	private ReconciliationCasePort reconciliationCasePort;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void opensACaseAndReadsItBack() {
		ReconciliationCase candidate = newCandidate(new PaymentIntentId(UUID.randomUUID()), "CAPTURED", "DECLINED:x");

		ReconciliationOpenOutcome outcome = reconciliationCasePort.open(candidate);

		assertThat(outcome).isInstanceOf(ReconciliationOpenOutcome.Opened.class);
		ReconciliationCase found = reconciliationCasePort.findById(candidate.id()).orElseThrow();
		assertThat(found.status()).isEqualTo(ReconciliationCaseStatus.OPEN);
		assertThat(found.severity()).isEqualTo(ReconciliationSeverity.HIGH);
		assertThat(found.resolutionIfPresent()).isEmpty();
	}

	@Test
	void openingTheSameFingerprintTwiceReturnsTheExistingOpenCaseWithoutDuplicating() {
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.randomUUID());
		ReconciliationCase first = newCandidate(paymentIntentId, "CAPTURED", "DECLINED:x");
		ReconciliationCase duplicateAttempt = newCandidate(paymentIntentId, "CAPTURED", "DECLINED:x");

		ReconciliationOpenOutcome firstOutcome = reconciliationCasePort.open(first);
		ReconciliationOpenOutcome secondOutcome = reconciliationCasePort.open(duplicateAttempt);

		assertThat(firstOutcome).isInstanceOf(ReconciliationOpenOutcome.Opened.class);
		assertThat(secondOutcome).isInstanceOf(ReconciliationOpenOutcome.AlreadyOpen.class);
		assertThat(((ReconciliationOpenOutcome.AlreadyOpen) secondOutcome).existing().id()).isEqualTo(first.id());

		Integer count = jdbcTemplate.queryForObject(
			"select count(*) from reconciliation_cases where fingerprint = ?", Integer.class, first.fingerprint()
		);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void aNewCaseCanOpenAfterThePreviousOneWithTheSameFingerprintWasResolved() {
		PaymentIntentId paymentIntentId = new PaymentIntentId(UUID.randomUUID());
		ReconciliationCase first = newCandidate(paymentIntentId, "CAPTURED", "DECLINED:x");
		reconciliationCasePort.open(first);
		reconciliationCasePort.resolve(first.id(), new ReconciliationResolution(
			new OperatorId(UUID.randomUUID()), "acknowledged", ReconciliationResolutionAction.ACKNOWLEDGE_NO_ACTION, null, Instant.now()
		));

		ReconciliationCase second = newCandidate(paymentIntentId, "CAPTURED", "DECLINED:x");
		ReconciliationOpenOutcome outcome = reconciliationCasePort.open(second);

		assertThat(outcome).isInstanceOf(ReconciliationOpenOutcome.Opened.class);
		List<Object> fingerprints = jdbcTemplate.queryForList(
			"select id from reconciliation_cases where fingerprint = ?", Object.class, first.fingerprint()
		);
		assertThat(fingerprints).hasSize(2);
	}

	@Test
	void resolvingSetsTheResolutionAndCannotBeRepeated() {
		ReconciliationCase candidate = newCandidate(new PaymentIntentId(UUID.randomUUID()), "AUTHORIZED", "DECLINED:x");
		reconciliationCasePort.open(candidate);
		ReconciliationResolution resolution = new ReconciliationResolution(
			new OperatorId(UUID.randomUUID()), "false positive, confirmed with provider", ReconciliationResolutionAction.IGNORE, null, Instant.now()
		);

		ReconciliationCase resolved = reconciliationCasePort.resolve(candidate.id(), resolution);

		assertThat(resolved.status()).isEqualTo(ReconciliationCaseStatus.IGNORED_WITH_REASON);
		assertThat(resolved.resolutionIfPresent()).isPresent();
		assertThat(resolved.resolutionIfPresent().get().reason()).isEqualTo("false positive, confirmed with provider");
		assertThat(resolved.localEvidence()).isEqualTo(candidate.localEvidence());
		assertThat(resolved.detectedAt()).isEqualTo(candidate.detectedAt());

		assertThatThrownBy(() -> reconciliationCasePort.resolve(candidate.id(), resolution)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void resolvedCaseEvidenceCannotBeTamperedWithEvenBelowTheApplicationLayer() {
		ReconciliationCase candidate = newCandidate(new PaymentIntentId(UUID.randomUUID()), "AUTHORIZED", "DECLINED:x");
		reconciliationCasePort.open(candidate);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"update reconciliation_cases set local_evidence = 'TAMPERED' where id = ?", candidate.id().value()
		)).isInstanceOf(Exception.class);
	}

	@Test
	void reconciliationCasesCannotBeDeletedEvenBelowTheApplicationLayer() {
		ReconciliationCase candidate = newCandidate(new PaymentIntentId(UUID.randomUUID()), "AUTHORIZED", "DECLINED:x");
		reconciliationCasePort.open(candidate);

		assertThatThrownBy(() -> jdbcTemplate.update(
			"delete from reconciliation_cases where id = ?", candidate.id().value()
		)).isInstanceOf(Exception.class);
	}

	@Test
	void findAllFiltersByStatus() {
		ReconciliationCase openCase = newCandidate(new PaymentIntentId(UUID.randomUUID()), "AUTHORIZED", "DECLINED:x");
		reconciliationCasePort.open(openCase);

		List<ReconciliationCase> openOnly = reconciliationCasePort.findAll(ReconciliationCaseStatus.OPEN);

		assertThat(openOnly).extracting(ReconciliationCase::id).contains(openCase.id());
		assertThat(openOnly).allMatch(kase -> kase.status() == ReconciliationCaseStatus.OPEN);
	}

	private static ReconciliationCase newCandidate(PaymentIntentId paymentIntentId, String localEvidence, String providerEvidence) {
		String fingerprint = ReconciliationCase.fingerprint(
			paymentIntentId, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE, localEvidence, providerEvidence
		);
		return new ReconciliationCase(
			ReconciliationCaseId.generate(), paymentIntentId, fingerprint, ReconciliationMismatchType.AUTHORIZATION_OUTCOME_DIVERGENCE,
			ReconciliationSeverity.HIGH, ReconciliationCaseStatus.OPEN, localEvidence, providerEvidence,
			Instant.now().truncatedTo(ChronoUnit.MICROS), null
		);
	}
}
