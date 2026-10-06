package com.smart.erp.spike.s1.tenancy;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code erp.platform.datasource.*}: the non-routed platform DB (doc §4.3, §4.5). */
@ConfigurationProperties(prefix = "erp.platform.datasource")
public record PlatformDataSourceProperties(String url, String username, String password) {

    public PlatformDataSourceProperties {
        Objects.requireNonNull(url, "erp.platform.datasource.url");
        Objects.requireNonNull(username, "erp.platform.datasource.username");
        Objects.requireNonNull(password, "erp.platform.datasource.password");
    }
}
