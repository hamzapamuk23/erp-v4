package com.smart.erp.spike.s2.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PlatformDataSourceConfiguration {

    /**
     * Not a default candidate: the tenant DataSource is the one unqualified consumers get (ADR-0015). Boot's health
     * still checks this one, so readiness follows the platform DB.
     */
    @Bean(defaultCandidate = false)
    @PlatformDb
    HikariDataSource platformDataSource(PlatformDataSourceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("platform");
        dataSource.setJdbcUrl(properties.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        return dataSource;
    }

    @Bean
    TenantDirectory tenantDirectory(@PlatformDb DataSource platformDataSource) {
        return new JdbcTenantDirectory(platformDataSource);
    }

    /** The only default-candidate DataSource; Phase 1 replaces it with the real routing DataSource. */
    @Bean
    TenantDataSourceStandIn tenantDataSource() {
        return new TenantDataSourceStandIn();
    }
}
