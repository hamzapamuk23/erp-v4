package com.smart.erp.spike.s1.tenancy;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code erp.tenancy.datasource.*}: how tenant pools are built (doc §4.5). {@code url-template} contains
 * {@code {database}}, replaced by the tenant's database name from the registry.
 */
@ConfigurationProperties(prefix = "erp.tenancy.datasource")
public record TenantDataSourceProperties(
        String urlTemplate,
        String username,
        String password,
        @DefaultValue("4") int maximumPoolSize,
        @DefaultValue("5m") Duration idleTimeout,
        @DefaultValue("10s") Duration connectionTimeout) {

    static final String DATABASE_PLACEHOLDER = "{database}";

    public TenantDataSourceProperties {
        Objects.requireNonNull(urlTemplate, "erp.tenancy.datasource.url-template");
        Objects.requireNonNull(username, "erp.tenancy.datasource.username");
        Objects.requireNonNull(password, "erp.tenancy.datasource.password");
        if (!urlTemplate.contains(DATABASE_PLACEHOLDER)) {
            throw new IllegalArgumentException(
                    "erp.tenancy.datasource.url-template must contain " + DATABASE_PLACEHOLDER + ": " + urlTemplate);
        }
    }
}
