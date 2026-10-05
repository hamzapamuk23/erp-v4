package com.smart.erp.spike.s1.tenancy;

import com.zaxxer.hikari.HikariDataSource;

/** Builds one HikariCP pool per tenant DB (doc §4.5). */
public final class TenantDataSourceFactory {

    private final TenantDataSourceProperties properties;

    public TenantDataSourceFactory(TenantDataSourceProperties properties) {
        this.properties = properties;
    }

    /**
     * The no-argument {@link HikariDataSource} constructor starts the pool on the first {@code getConnection()}, so
     * creating a pool never connects.
     */
    public HikariDataSource create(TenantDescriptor tenant) {
        HikariDataSource pool = new HikariDataSource();
        pool.setPoolName("tenant-" + tenant.key().value());
        pool.setJdbcUrl(properties
                .urlTemplate()
                .replace(TenantDataSourceProperties.DATABASE_PLACEHOLDER, tenant.databaseName()));
        pool.setUsername(properties.username());
        pool.setPassword(properties.password());
        pool.setMinimumIdle(0);
        pool.setMaximumPoolSize(properties.maximumPoolSize());
        pool.setIdleTimeout(properties.idleTimeout().toMillis());
        pool.setConnectionTimeout(properties.connectionTimeout().toMillis());
        return pool;
    }
}
