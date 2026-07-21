package io.github.vinicius.sentinel;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Sentinel Ledger")
@SpringBootApplication
@OpenAPIDefinition(
	info = @Info(
		title = "Sentinel Ledger API",
		version = "v1",
		description = "Payment orchestration with an immutable double-entry ledger, "
			+ "persistent idempotency, reconciliation, and observability."
	)
)
@SecurityScheme(name = "merchantBasicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class SentinelLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SentinelLedgerApplication.class, args);
	}
}
