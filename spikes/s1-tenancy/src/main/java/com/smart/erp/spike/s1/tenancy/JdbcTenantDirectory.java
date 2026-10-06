package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads the registry from the platform DB (never through the routing DataSource). */
final class JdbcTenantDirectory implements TenantDirectory {

    private final JdbcClient platform;

    JdbcTenantDirectory(JdbcClient platform) {
        this.platform = platform;
    }

    @Override
    public Optional<TenantDescriptor> find(TenantKey tenant) {
        return platform.sql("select tenant_key, database_name, status from tenant where tenant_key = ?")
                .param(tenant.value())
                .query(JdbcTenantDirectory::descriptor)
                .optional();
    }

    @Override
    public List<TenantKey> activeTenants() {
        return platform.sql("select tenant_key from tenant where status = 'ACTIVE' order by tenant_key")
                .query((rs, row) -> new TenantKey(rs.getString("tenant_key")))
                .list();
    }

    private static TenantDescriptor descriptor(ResultSet rs, int row) throws SQLException {
        return new TenantDescriptor(
                new TenantKey(rs.getString("tenant_key")),
                rs.getString("database_name"),
                TenantStatus.valueOf(rs.getString("status")));
    }
}
