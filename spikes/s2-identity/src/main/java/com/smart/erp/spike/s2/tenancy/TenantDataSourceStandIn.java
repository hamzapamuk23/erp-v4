package com.smart.erp.spike.s2.tenancy;

import com.smart.erp.spike.s2.kernel.MissingTenantContextException;
import com.smart.erp.spike.s2.kernel.TenantContext;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Stands in for Phase 1's tenant-routing DataSource: the only default-candidate DataSource, so every unqualified
 * consumer (Boot's health and metrics, a misrouted session or security component) would reach it. It never hands out a
 * connection; every attempt is counted and refused, which turns "this component touched the tenant database" into a
 * number the tests can assert. Extends {@link AbstractRoutingDataSource} so Boot recognises it as a routing DataSource
 * ({@code management.health.db.ignore-routing-data-sources}).
 */
public final class TenantDataSourceStandIn extends AbstractRoutingDataSource {

    private final AtomicLong requests = new AtomicLong();

    public TenantDataSourceStandIn() {
        setTargetDataSources(Map.of());
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.current().orElse(null);
    }

    @Override
    protected DataSource determineTargetDataSource() {
        requests.incrementAndGet();
        throw new MissingTenantContextException();
    }

    /**
     * Spring's default routes {@code unwrap} to the current target. Boot's metrics and health binding call it with no
     * tenant bound and swallow the failure, so it must not route (and is not counted).
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("A tenant DataSource does not expose a tenant's pool: " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    /** Connection requests so far; counts also those whose exception the caller swallowed. */
    public long requests() {
        return requests.get();
    }
}
