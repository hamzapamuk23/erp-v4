package com.smart.erp.platformtest.postgres;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The one place that decides which PostgreSQL image tests run against (doc §12.1). */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    public static final String IMAGE = "postgres:18.6";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE);
    }
}
