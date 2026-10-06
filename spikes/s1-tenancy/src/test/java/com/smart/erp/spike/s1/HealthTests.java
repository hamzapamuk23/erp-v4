package com.smart.erp.spike.s1;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.support.SpikeTest;
import com.smart.erp.spike.s1.tenancy.TenantRoutingDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpikeTest
class HealthTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TenantRoutingDataSource routing;

    @Test
    void readinessChecksThePlatformDatabaseAndNoTenant() {
        long before = routing.rejectedWithoutTenant();

        assertThat(mvc.get().uri("/actuator/health/readiness"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.components.db.status")
                .isEqualTo("UP");

        assertThat(routing.rejectedWithoutTenant()).isEqualTo(before);
    }
}
