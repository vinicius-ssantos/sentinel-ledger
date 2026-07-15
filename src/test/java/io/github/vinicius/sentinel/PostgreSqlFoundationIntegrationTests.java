package io.github.vinicius.sentinel;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostgreSqlFoundationIntegrationTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void runsAgainstPostgreSqlAndAppliesTheBaselineMigration() {
		String databaseProduct = jdbcTemplate.queryForObject("select version()", String.class);
		Integer appliedBaseline = jdbcTemplate.queryForObject(
			"select count(*) from flyway_schema_history where version = '1' and success",
			Integer.class
		);

		assertThat(databaseProduct).startsWith("PostgreSQL");
		assertThat(appliedBaseline).isEqualTo(1);
		assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
	}
}
