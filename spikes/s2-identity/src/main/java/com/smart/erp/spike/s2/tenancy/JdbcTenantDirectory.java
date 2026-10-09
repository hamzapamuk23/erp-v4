package com.smart.erp.spike.s2.tenancy;

import com.smart.erp.spike.s2.kernel.TenantKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads the registry from the platform DB (never through the tenant DataSource). No cache: the status is read on every
 * lookup. Phase 2 adds Caffeine with LISTEN/NOTIFY invalidation (ADR-0016).
 */
final class JdbcTenantDirectory implements TenantDirectory {

    private static final String SELECT = "select tenant_key, status, oidc_issuer, organization_alias from ";

    private final JdbcClient platform;

    JdbcTenantDirectory(DataSource platformDataSource) {
        this.platform = JdbcClient.create(platformDataSource);
    }

    @Override
    public Optional<TenantRecord> findByHost(String normalizedHost) {
        return platform.sql(SELECT + "tenant_domain join tenant using (tenant_key) where host = ?")
                .param(normalizedHost)
                .query(JdbcTenantDirectory::record)
                .optional();
    }

    @Override
    public Optional<TenantRecord> findByIdentity(String issuer, String organizationAlias) {
        return platform.sql(SELECT + "tenant where oidc_issuer = ? and organization_alias = ?")
                .param(issuer)
                .param(organizationAlias)
                .query(JdbcTenantDirectory::record)
                .optional();
    }

    @Override
    public TenantRecord require(TenantKey key) {
        return platform.sql(SELECT + "tenant where tenant_key = ?")
                .param(key.value())
                .query(JdbcTenantDirectory::record)
                .optional()
                .orElseThrow(() -> new IllegalStateException("Tenant is not registered: " + key));
    }

    private static TenantRecord record(ResultSet rs, int row) throws SQLException {
        return new TenantRecord(
                new TenantKey(rs.getString("tenant_key")),
                TenantStatus.valueOf(rs.getString("status")),
                rs.getString("oidc_issuer"),
                rs.getString("organization_alias"));
    }
}
