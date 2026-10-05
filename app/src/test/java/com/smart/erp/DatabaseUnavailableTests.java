package com.smart.erp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Review focus 1: a missing database degrades health but never takes the application down. */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/erp_platform",
            "spring.datasource.hikari.connection-timeout=1000"
        })
@AutoConfigureMockMvc
class DatabaseUnavailableTests {

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
