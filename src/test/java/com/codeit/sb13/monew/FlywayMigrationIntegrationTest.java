package com.codeit.sb13.monew;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("migration")
@SpringBootTest(properties = {
        "spring.datasource.url=${MONEW_MIGRATION_DB_URL:jdbc:postgresql://localhost:5432/monew}",
        "spring.datasource.username=${MONEW_MIGRATION_DB_USERNAME:monew}",
        "spring.datasource.password=${MONEW_MIGRATION_DB_PASSWORD:change-me}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.docker.compose.enabled=false"
})
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsAreApplied() {
        Long appliedCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true",
                Long.class
        );
        Long failedCount = jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = false",
                Long.class
        );

        assertThat(appliedCount).isNotNull().isPositive();
        assertThat(failedCount).isNotNull().isZero();
    }
}
