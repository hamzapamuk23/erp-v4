package com.smart.erp.spike.s1;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.support.SpikeContexts;
import com.smart.erp.spike.s1.tenancy.TenantRoutingDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The central S1 claim (doc §4.4–§4.5, ADR-0015): no framework component asks the routing DataSource for a tenant
 * connection while no tenant is bound — not at start-up, not while binding health and metrics, not at shutdown. The
 * counter also sees attempts whose exception the caller swallowed. Each later task adds a component and keeps this green.
 */
class StartupIsolationTests {

    @Test
    void startupAndShutdownNeverAskForATenantConnection() {
        TenantRoutingDataSource routing;
        try (ConfigurableApplicationContext context = SpikeContexts.start()) {
            routing = context.getBean(TenantRoutingDataSource.class);
            assertThat(routing.rejectedWithoutTenant())
                    .as("tenant-less connection requests during start-up")
                    .isZero();
            assertThat(routing.openPools())
                    .as("tenant pools opened during start-up")
                    .isEmpty();
        }
        assertThat(routing.rejectedWithoutTenant())
                .as("tenant-less connection requests during shutdown")
                .isZero();
    }
}
