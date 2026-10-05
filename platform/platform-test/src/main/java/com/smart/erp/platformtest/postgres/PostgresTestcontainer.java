package com.smart.erp.platformtest.postgres;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The one place that decides which PostgreSQL image tests run against (doc §12.1). */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    public static final String IMAGE = "postgres:18.6";

    /** Same initdb arguments as deploy/compose/compose.yaml (doc §7.8). */
    private static final String INITDB_ARGS = "--encoding=UTF8 --locale-provider=builtin --builtin-locale=C.UTF-8";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(IMAGE).withEnv("POSTGRES_INITDB_ARGS", INITDB_ARGS);
    }
}
