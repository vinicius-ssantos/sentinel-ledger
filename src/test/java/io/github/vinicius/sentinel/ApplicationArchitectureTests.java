package io.github.vinicius.sentinel;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ApplicationArchitectureTests {

	private static final ApplicationModules MODULES = ApplicationModules.of(SentinelLedgerApplication.class);

	@Test
	void verifiesApplicationModuleStructure() {
		MODULES.verify();
	}

	@Test
	void writesApplicationModuleDocumentation() {
		new Documenter(MODULES)
			.writeModulesAsPlantUml()
			.writeIndividualModulesAsPlantUml()
			.writeModuleCanvases()
			.writeAggregatingDocument();
	}
}
