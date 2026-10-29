package com.schoollink;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL 16 for integration tests, the same major version as production (SPEC §8).
 * Import it with {@code @Import(TestcontainersConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer("postgres:16-alpine");
	}

}
