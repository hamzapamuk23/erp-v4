package com.smart.erp.platformtest.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PostgresTestcontainerTests {

    @Test
    void newContainerUsesTheProductionImageAndInitdbArguments() {
        PostgreSQLContainer container = PostgresTestcontainer.newContainer();
        assertThat(container.getDockerImageName()).isEqualTo(PostgresTestcontainer.IMAGE);
        assertThat(container.getEnvMap())
                .containsEntry(
                        "POSTGRES_INITDB_ARGS", "--encoding=UTF8 --locale-provider=builtin --builtin-locale=C.UTF-8");
    }
}
