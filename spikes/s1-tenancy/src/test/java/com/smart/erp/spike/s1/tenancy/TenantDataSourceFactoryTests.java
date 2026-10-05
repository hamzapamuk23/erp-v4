package com.smart.erp.spike.s1.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TenantDataSourceFactoryTests {

    private static final TenantDataSourceProperties PROPERTIES = new TenantDataSourceProperties(
            "jdbc:postgresql://127.0.0.1:1/{database}",
            "erp_tenant",
            "unused",
            4,
            Duration.ofMinutes(5),
            Duration.ofSeconds(10));

    @Test
    void poolsFollowTheDocumentedLimits() {
        TenantKey acme = new TenantKey("acme");
        try (HikariDataSource pool = new TenantDataSourceFactory(PROPERTIES)
                .create(new TenantDescriptor(acme, "erp_t_acme", TenantStatus.ACTIVE))) {
            assertThat(pool.getPoolName()).isEqualTo("tenant-acme");
            assertThat(pool.getJdbcUrl()).isEqualTo("jdbc:postgresql://127.0.0.1:1/erp_t_acme");
            assertThat(pool.getUsername()).isEqualTo("erp_tenant");
            // doc §4.5: minIdle=0, max=4, idle connections close after 5 minutes
            assertThat(pool.getMinimumIdle()).isZero();
            assertThat(pool.getMaximumPoolSize()).isEqualTo(4);
            assertThat(pool.getIdleTimeout()).isEqualTo(Duration.ofMinutes(5).toMillis());
            assertThat(pool.isRunning())
                    .as("no connection is opened when the pool is created")
                    .isFalse();
        }
    }

    @Test
    void aTemplateWithoutTheDatabasePlaceholderIsRejected() {
        assertThatThrownBy(() -> new TenantDataSourceProperties(
                        "jdbc:postgresql://localhost/erp", "u", "p", 4, Duration.ofMinutes(5), Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{database}");
    }
}
