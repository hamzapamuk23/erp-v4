package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * The one DataSource that JPA, jOOQ, JdbcClient and Modulith use (ADR-0015). It routes to the pool of the tenant bound
 * in {@link TenantContext}; there is no static target map and no default target. Pools are created lazily, only for
 * ACTIVE tenants. Extends {@link AbstractRoutingDataSource} so Boot recognises it as a routing DataSource
 * ({@code management.health.db.ignore-routing-data-sources}).
 */
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final TenantDirectory directory;
    private final TenantDataSourceFactory factory;
    private final ConcurrentMap<TenantKey, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final AtomicLong rejectedWithoutTenant = new AtomicLong();

    public TenantRoutingDataSource(TenantDirectory directory, TenantDataSourceFactory factory) {
        this.directory = directory;
        this.factory = factory;
        setTargetDataSources(Map.of());
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.current().orElse(null);
    }

    @Override
    protected DataSource determineTargetDataSource() {
        TenantKey tenant = TenantContext.current().orElse(null);
        if (tenant == null) {
            rejectedWithoutTenant.incrementAndGet();
            throw new MissingTenantContextException();
        }
        HikariDataSource pool = pools.get(tenant);
        if (pool != null) {
            return pool;
        }
        TenantDescriptor descriptor =
                directory.find(tenant).orElseThrow(() -> new TenantNotAvailableException(tenant, "unknown tenant"));
        if (descriptor.status() != TenantStatus.ACTIVE) {
            throw new TenantNotAvailableException(tenant, descriptor.status().name());
        }
        return pools.computeIfAbsent(tenant, key -> factory.create(descriptor));
    }

    /**
     * Spring's default routes {@code unwrap} to the current target. Boot's metrics and health binding call it with no
     * tenant bound and swallow the failure, so it must not route.
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("A tenant-routing DataSource does not expose a tenant's pool: " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    /** Requests made while no tenant was bound; counts also those whose exception the caller swallowed. */
    public long rejectedWithoutTenant() {
        return rejectedWithoutTenant.get();
    }

    public Set<TenantKey> openPools() {
        return Set.copyOf(pools.keySet());
    }

    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
