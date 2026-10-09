package com.smart.erp.spike.s2;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeContexts;
import com.smart.erp.spike.s2.support.SpikeKeycloak;
import com.smart.erp.spike.s2.tenancy.TenantDataSourceStandIn;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * S2's share of S1's central claim (S1 open item 1): with the servlet stack, Spring Security, Spring Session and
 * Actuator in place, nothing asks the default (tenant) DataSource for a connection — not at start-up, not during a full
 * login, not at shutdown. The bearer chain is part of that: an integration client's call resolves its tenant from the
 * token and the platform DB alone.
 */
class StartupIsolationTests {

    @Test
    void sessionsSecurityAndActuatorNeverAskForTheTenantDataSource() {
        TenantDataSourceStandIn standIn;
        try (ConfigurableApplicationContext context = SpikeContexts.start()) {
            standIn = context.getBean(TenantDataSourceStandIn.class);
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            SpikeBrowser browser = new SpikeBrowser(port);

            assertThat(browser.get("http://127.0.0.1:" + port + "/actuator/health/readiness")
                            .status())
                    .isEqualTo(200);
            assertThat(browser.login("acme.erp.test", "ayse").body()).isEqualTo("ok");
            assertThat(browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json")
                            .status())
                    .isEqualTo(200);
            assertThat(browser.get(
                                    "https://api.erp.test/api/whoami",
                                    "Authorization",
                                    "Bearer " + SpikeKeycloak.clientCredentials("acme-integration"))
                            .status())
                    .isEqualTo(200);
            assertThat(standIn.requests())
                    .as("tenant DataSource requests while running")
                    .isZero();
        }
        assertThat(standIn.requests())
                .as("tenant DataSource requests at shutdown")
                .isZero();
    }
}
