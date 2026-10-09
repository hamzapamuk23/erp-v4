package com.smart.erp.spike.s2.support;

import com.smart.erp.platformtest.postgres.PostgresTestcontainer;
import com.smart.erp.spike.s2.kernel.TenantKey;
import com.smart.erp.spike.s2.tenancy.TenantRecord;
import com.smart.erp.spike.s2.tenancy.TenantStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 18 server per test run holding the platform DB of doc §7.1, owned by its own role and closed to
 * PUBLIC. S2 needs no tenant database: the tenant directory and the sessions live in the platform DB. Plays the part of
 * {@code erpctl migrate}: migrations run here, never at application start-up (doc §4.6).
 */
public final class SpikeDatabases {

    public static final String PLATFORM_DATABASE = "erp_platform";

    private static final String PLATFORM_ROLE = "erp_platform";
    // Generated per run so the repository holds no credential literals (K9).
    private static final String PLATFORM_PASSWORD = UUID.randomUUID().toString();

    private static final PostgreSQLContainer POSTGRES = PostgresTestcontainer.newContainer();

    static {
        POSTGRES.start();
        createRoleAndDatabase();
        Flyway.configure()
                .dataSource(jdbcUrl(PLATFORM_DATABASE), PLATFORM_ROLE, PLATFORM_PASSWORD)
                .locations("classpath:db/platform")
                .load()
                .migrate();
    }

    private SpikeDatabases() {}

    public static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    /** What an installer would write into the application's configuration. */
    public static Map<String, String> applicationProperties() {
        return Map.of(
                "erp.platform.datasource.url", jdbcUrl(PLATFORM_DATABASE),
                "erp.platform.datasource.username", PLATFORM_ROLE,
                "erp.platform.datasource.password", PLATFORM_PASSWORD);
    }

    /** The platform DB as the application's own role: what the tenant directory sees. */
    public static DataSource platformDataSource() {
        return new DriverManagerDataSource(jdbcUrl(PLATFORM_DATABASE), PLATFORM_ROLE, PLATFORM_PASSWORD);
    }

    /** Superuser view of the platform DB that bypasses the application's role: the independent witness. */
    public static JdbcClient platformDatabase() {
        return JdbcClient.create(new DriverManagerDataSource(
                jdbcUrl(PLATFORM_DATABASE), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    /** Registers a tenant and its hosts. Idempotent: registering again resets the tenant, including its status. */
    public static void register(TenantRecord tenant, String... hosts) {
        JdbcClient platform = JdbcClient.create(platformDataSource());
        platform.sql("""
                        insert into tenant (tenant_key, status, oidc_issuer, organization_alias)
                        values (?, ?, ?, ?)
                        on conflict (tenant_key) do update
                            set status = excluded.status,
                                oidc_issuer = excluded.oidc_issuer,
                                organization_alias = excluded.organization_alias
                        """)
                .param(tenant.key().value())
                .param(tenant.status().name())
                .param(tenant.issuer())
                .param(tenant.organizationAlias())
                .update();
        for (String host : hosts) {
            platform.sql("""
                            insert into tenant_domain (host, tenant_key) values (?, ?)
                            on conflict (host) do update set tenant_key = excluded.tenant_key
                            """).param(host).param(tenant.key().value()).update();
        }
    }

    public static void setStatus(TenantKey tenant, TenantStatus status) {
        int updated = JdbcClient.create(platformDataSource())
                .sql("update tenant set status = ? where tenant_key = ?")
                .param(status.name())
                .param(tenant.value())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Tenant is not registered: " + tenant);
        }
    }

    private static void createRoleAndDatabase() {
        List<String> statements = List.of(
                "create role " + PLATFORM_ROLE + " login password '" + PLATFORM_PASSWORD + "'",
                "create database " + PLATFORM_DATABASE + " owner " + PLATFORM_ROLE,
                "revoke connect on database " + PLATFORM_DATABASE + " from public");
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the spike platform database", e);
        }
    }
}
