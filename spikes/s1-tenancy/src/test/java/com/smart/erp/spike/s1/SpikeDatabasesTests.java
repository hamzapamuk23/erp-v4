package com.smart.erp.spike.s1;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeDatabases;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpikeDatabasesTests {

    @Test
    void createsThePlatformAndTenantDatabasesButNotTheGhost() {
        List<String> databases = SpikeDatabases.server()
                .sql("select datname from pg_database")
                .query(String.class)
                .list();
        assertThat(databases)
                .contains("erp_platform", "erp_t_acme", "erp_t_globex", "erp_t_initech")
                .doesNotContain("erp_t_ghost");
    }

    @Test
    void registersTheFixtureTenants() {
        List<String> tenants = SpikeDatabases.platformDatabase()
                .sql("select tenant_key || '=' || status from tenant order by tenant_key")
                .query(String.class)
                .list();
        assertThat(tenants).containsExactly("acme=ACTIVE", "ghost=ACTIVE", "globex=ACTIVE", "initech=SUSPENDED");
    }

    @Test
    void migratesEveryTenantDatabase() {
        for (TenantKey tenant : List.of(ACME, GLOBEX, INITECH)) {
            assertThat(SpikeDatabases.tenantDatabase(tenant)
                            .sql("select to_regclass('sample.sample_record')::text")
                            .query(String.class)
                            .single())
                    .isEqualTo("sample.sample_record");
            assertThat(SpikeDatabases.tenantDatabase(tenant)
                            .sql("select to_regclass('platform_events.event_publication')::text")
                            .query(String.class)
                            .single())
                    .isEqualTo("platform_events.event_publication");
        }
    }

    @Test
    void theTenantRoleCannotConnectToThePlatformDatabase() {
        assertThatThrownBy(() -> {
                    try (Connection connection = SpikeDatabases.connectAsTenantRole(SpikeDatabases.PLATFORM_DATABASE)) {
                        connection.isValid(1);
                    }
                })
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied for database");
    }
}
