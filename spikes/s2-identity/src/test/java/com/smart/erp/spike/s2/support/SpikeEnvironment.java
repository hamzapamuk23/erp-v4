package com.smart.erp.spike.s2.support;

import com.smart.erp.spike.s2.kernel.TenantKey;
import com.smart.erp.spike.s2.tenancy.TenantRecord;
import com.smart.erp.spike.s2.tenancy.TenantStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * The environment every spike application context runs in: both fixtures started, the plan's three tenants registered
 * in the platform DB against the fixture's issuer (once per test run), and the settings an installer would write. The
 * properties rank below command-line arguments and test property sources, so a test can override one
 * (KeycloakUnavailableTests), and above the OS environment and application.yaml.
 */
public final class SpikeEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Map<String, Object> PROPERTIES = registerTenants();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        context.getEnvironment()
                .getPropertySources()
                .addBefore(
                        StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
                        new MapPropertySource("spikeEnvironment", PROPERTIES));
    }

    /** Plan: test users and tenants. initech is ACTIVE here; the suspension test sets and resets it. */
    private static Map<String, Object> registerTenants() {
        String issuer = SpikeKeycloak.issuer();
        for (String tenant : List.of("acme", "globex", "initech")) {
            SpikeDatabases.register(
                    new TenantRecord(new TenantKey(tenant), TenantStatus.ACTIVE, issuer, tenant), tenant + ".erp.test");
        }
        Map<String, Object> properties = new LinkedHashMap<>(SpikeDatabases.applicationProperties());
        properties.putAll(SpikeKeycloak.applicationProperties());
        return Map.copyOf(properties);
    }
}
