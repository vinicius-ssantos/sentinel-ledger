package io.github.vinicius.sentinel.observability;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dashboard and alert files under docs/observability/ are dashboard-as-code artifacts, not deployed
 * configuration (see docs/OBSERVABILITY.md) -- but they still need to be well-formed and, for alerts, satisfy the
 * project's own bar that "every alert names an operator action or investigation path".
 */
class ObservabilityArtifactsTests {

	private static final Path DASHBOARD = Path.of("docs/observability/reconciliation-dashboard.json");
	private static final Path ALERTS = Path.of("docs/observability/alerts.yml");
	private static final Pattern ALERT_NAME = Pattern.compile("(?m)^\\s*- alert:\\s*(\\S+)");

	@Test
	void theReconciliationDashboardIsWellFormedJson() throws IOException {
		String content = Files.readString(DASHBOARD);

		JsonNode root = new ObjectMapper().readTree(content);

		assertThat(root.path("panels").isArray()).isTrue();
		assertThat(root.path("panels").size()).isGreaterThan(0);
	}

	@Test
	void everyAlertNamesAnOperatorActionOrInvestigationPath() throws IOException {
		String content = Files.readString(ALERTS);
		List<String> alertNames = extractAlertNames(content);
		assertThat(alertNames).isNotEmpty();

		String[] blocks = content.split("(?=(?m)^\\s*- alert:)");
		for (String block : blocks) {
			Matcher nameMatcher = ALERT_NAME.matcher(block);
			if (!nameMatcher.find()) {
				continue;
			}
			String alertName = nameMatcher.group(1);
			assertThat(block).as("alert %s must define a summary", alertName).contains("summary:");
			assertThat(block).as("alert %s must define a description", alertName).contains("description:");

			String description = block.substring(block.indexOf("description:"));
			assertThat(description.length()).as("alert %s description must not be trivially short", alertName).isGreaterThan(60);
		}
	}

	private static List<String> extractAlertNames(String content) {
		Matcher matcher = ALERT_NAME.matcher(content);
		return matcher.results().map(m -> m.group(1)).toList();
	}
}
