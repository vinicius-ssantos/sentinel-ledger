package io.github.vinicius.sentinel.reconciliation.internal;

import io.github.vinicius.sentinel.reconciliation.ReconciliationCasePort;
import io.github.vinicius.sentinel.reconciliation.ReconciliationCaseStatus;
import io.github.vinicius.sentinel.reconciliation.ReconciliationSeverity;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/**
 * One gauge per severity (a fixed two-value enum, so cardinality is bounded), each re-querying the case store on
 * every scrape rather than tracking a running count -- correctness over efficiency for the case counts a
 * reconciliation dashboard actually needs.
 */
@Component
class ReconciliationOpenCaseGauges {

	ReconciliationOpenCaseGauges(ReconciliationCasePort reconciliationCasePort, MeterRegistry meterRegistry) {
		for (ReconciliationSeverity severity : ReconciliationSeverity.values()) {
			Gauge.builder("sentinel.reconciliation.cases.open", reconciliationCasePort, port -> countOpen(port, severity))
				.description("Currently open or investigating reconciliation cases, by severity")
				.tag("severity", severity.name())
				.register(meterRegistry);
		}
	}

	private static long countOpen(ReconciliationCasePort port, ReconciliationSeverity severity) {
		return Stream.concat(
				port.findAll(ReconciliationCaseStatus.OPEN).stream(),
				port.findAll(ReconciliationCaseStatus.INVESTIGATING).stream()
			)
			.filter(reconciliationCase -> reconciliationCase.severity() == severity)
			.count();
	}
}
