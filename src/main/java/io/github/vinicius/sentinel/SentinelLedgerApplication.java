package io.github.vinicius.sentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Sentinel Ledger")
@SpringBootApplication
public class SentinelLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SentinelLedgerApplication.class, args);
	}
}
