package com.smart.erp.spike.s1.tenancy;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
class TenancyConfiguration {

    /**
     * Not a default candidate: JPA, jOOQ, JdbcClient and Modulith must get the routing DataSource (ADR-0015). Boot's
     * health still checks it, so readiness follows the platform DB.
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
        return new JdbcTenantDirectory(JdbcClient.create(platformDataSource));
    }

    @Bean
    TenantRoutingDataSource tenantDataSource(TenantDirectory directory, TenantDataSourceProperties properties) {
        return new TenantRoutingDataSource(directory, new TenantDataSourceFactory(properties));
    }

    /** Static: a BeanPostProcessor must exist before the beans it intercepts are created. */
    @Bean
    static ModulithTenancySupport modulithTenancySupport() {
        return new ModulithTenancySupport();
    }

    /** Boot composes every TaskDecorator bean into the application task executor (@Async, Modulith listeners). */
    @Bean
    TenantTaskDecorator tenantTaskDecorator() {
        return new TenantTaskDecorator();
    }
}
