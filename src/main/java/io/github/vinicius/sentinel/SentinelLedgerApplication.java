package io.github.vinicius.sentinel;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableScheduling;

@Modulithic(systemName = "Sentinel Ledger")
@SpringBootApplication
@EnableScheduling
@OpenAPIDefinition(
	info = @Info(
		title = "Sentinel Ledger API",
		version = "v1",
		description = "Payment orchestration with an immutable double-entry ledger, "
			+ "persistent idempotency, reconciliation, and observability."
	)
)
@SecuritySchemes({
	@SecurityScheme(name = "merchantBasicAuth", type = SecuritySchemeType.HTTP, scheme = "basic"),
	@SecurityScheme(name = "operatorBasicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
})
public class SentinelLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SentinelLedgerApplication.class, args);
	}
}
