package com.smart.erp;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.platformtest.postgres.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Review focus 1, second half: the database is reachable but the configured password is wrong. The
 * application must still start; readiness goes DOWN while liveness and the system info API answer.
 */
@SpringBootTest(properties = "spring.datasource.hikari.connection-timeout=1000")
@AutoConfigureMockMvc
class DatabaseWrongPasswordTests {

    // Plain container (no @ServiceConnection): its real password differs from the one configured below.
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestcontainer.IMAGE);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", () -> POSTGRES.getPassword() + "-wrong");
    }

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthAndReadinessAreDown() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("DOWN");
        assertThat(mvc.get().uri("/actuator/health/readiness")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void livenessAndApiStillAnswer() {
        assertThat(mvc.get().uri("/actuator/health/liveness")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/system/info")).hasStatusOk();
    }
}
