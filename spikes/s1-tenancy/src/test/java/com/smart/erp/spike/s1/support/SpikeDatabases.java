package com.smart.erp.spike.s1.support;

import com.smart.erp.platformtest.postgres.PostgresTestcontainer;
import com.smart.erp.spike.s1.kernel.TenantKey;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 18 server per test run holding the databases of doc §7.1: the platform DB and one DB per tenant, each
 * owned by its own role and closed to PUBLIC. Plays the part of {@code erpctl migrate}: migrations run here, never at
 * application start-up (doc §4.6).
 */
public final class SpikeDatabases {

    public static final TenantKey ACME = new TenantKey("acme");
    public static final TenantKey GLOBEX = new TenantKey("globex");
    /** Registered as SUSPENDED; its database exists and is migrated. */
    public static final TenantKey INITECH = new TenantKey("initech");
    /** Registered as ACTIVE, but its database was never created: an unreachable tenant. */
    public static final TenantKey GHOST = new TenantKey("ghost");

    public static final String PLATFORM_DATABASE = "erp_platform";

    private static final String PLATFORM_ROLE = "erp_platform";
    private static final String TENANT_ROLE = "erp_tenant";
    // Generated per run so the repository holds no credential literals (K9).
    private static final String PLATFORM_PASSWORD = UUID.randomUUID().toString();
    private static final String TENANT_PASSWORD = UUID.randomUUID().toString();
    private static final List<TenantKey> TENANTS_WITH_A_DATABASE = List.of(ACME, GLOBEX, INITECH);

    private static final PostgreSQLContainer POSTGRES = PostgresTestcontainer.newContainer();

    static {
        POSTGRES.start();
        createRolesAndDatabases();
        migrate(PLATFORM_DATABASE, PLATFORM_ROLE, PLATFORM_PASSWORD, "classpath:db/platform");
        for (TenantKey tenant : TENANTS_WITH_A_DATABASE) {
            migrate(databaseName(tenant), TENANT_ROLE, TENANT_PASSWORD, "classpath:db/tenant");
        }
        registerTenants();
    }

    private SpikeDatabases() {}

    public static String databaseName(TenantKey tenant) {
        return "erp_t_" + tenant.value();
    }

    public static String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
    }

    /** What an installer would write into the application's configuration. */
    public static Map<String, String> applicationProperties() {
        return Map.of(
                "erp.platform.datasource.url", jdbcUrl(PLATFORM_DATABASE),
                "erp.platform.datasource.username", PLATFORM_ROLE,
                "erp.platform.datasource.password", PLATFORM_PASSWORD,
                "erp.tenancy.datasource.url-template", jdbcUrl("{database}"),
                "erp.tenancy.datasource.username", TENANT_ROLE,
                "erp.tenancy.datasource.password", TENANT_PASSWORD);
    }

    /** Superuser view of a tenant DB that bypasses the application's routing: the independent witness for isolation. */
    public static JdbcClient tenantDatabase(TenantKey tenant) {
        return superuser(databaseName(tenant));
    }

    public static JdbcClient platformDatabase() {
        return superuser(PLATFORM_DATABASE);
    }

    public static JdbcClient server() {
        return superuser(POSTGRES.getDatabaseName());
    }

    public static Connection connectAsTenantRole(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), TENANT_ROLE, TENANT_PASSWORD);
    }

    private static JdbcClient superuser(String database) {
        return JdbcClient.create(
                new DriverManagerDataSource(jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    private static void createRolesAndDatabases() {
        List<String> statements = new ArrayList<>(List.of(
                "create role " + PLATFORM_ROLE + " login password '" + PLATFORM_PASSWORD + "'",
                "create database " + PLATFORM_DATABASE + " owner " + PLATFORM_ROLE,
                "revoke connect on database " + PLATFORM_DATABASE + " from public",
                "create role " + TENANT_ROLE + " login password '" + TENANT_PASSWORD + "'"));
        for (TenantKey tenant : TENANTS_WITH_A_DATABASE) {
            statements.add("create database " + databaseName(tenant) + " owner " + TENANT_ROLE);
            statements.add("revoke connect on database " + databaseName(tenant) + " from public");
        }
        try (Connection connection = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the spike databases", e);
        }
    }

    private static void migrate(String database, String role, String password, String location) {
        Flyway.configure()
                .dataSource(jdbcUrl(database), role, password)
                .locations(location)
                .load()
                .migrate();
    }

    private static void registerTenants() {
        JdbcClient platform = JdbcClient.create(
                new DriverManagerDataSource(jdbcUrl(PLATFORM_DATABASE), PLATFORM_ROLE, PLATFORM_PASSWORD));
        for (TenantKey tenant : List.of(ACME, GLOBEX, INITECH, GHOST)) {
            platform.sql("insert into tenant (tenant_key, database_name, status) values (?, ?, ?)")
                    .param(tenant.value())
                    .param(databaseName(tenant))
                    .param(INITECH.equals(tenant) ? "SUSPENDED" : "ACTIVE")
                    .update();
        }
    }
}
