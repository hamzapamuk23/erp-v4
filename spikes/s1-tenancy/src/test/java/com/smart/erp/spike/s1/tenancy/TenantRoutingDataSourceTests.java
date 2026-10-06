package com.smart.erp.spike.s1.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.boot.jdbc.EmbeddedDatabaseConnection;

class TenantRoutingDataSourceTests {

    private static final TenantKey ACME = new TenantKey("acme");
    private static final TenantKey INITECH = new TenantKey("initech");
    private static final TenantKey UNKNOWN = new TenantKey("unknown");

    // Points at a closed port: these tests must never open a connection.
    private final TenantRoutingDataSource routing = new TenantRoutingDataSource(
            new InMemoryDirectory(Map.of(
                    ACME, new TenantDescriptor(ACME, "erp_t_acme", TenantStatus.ACTIVE),
                    INITECH, new TenantDescriptor(INITECH, "erp_t_initech", TenantStatus.SUSPENDED))),
            new TenantDataSourceFactory(new TenantDataSourceProperties(
                    "jdbc:postgresql://127.0.0.1:1/{database}",
                    "nobody",
                    "unused",
                    4,
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(1))));

    @AfterEach
    void closePools() {
        routing.close();
    }

    @Test
    void aConnectionWithoutATenantIsRefusedAndCounted() {
        assertThatThrownBy(routing::getConnection).isInstanceOf(MissingTenantContextException.class);
        assertThat(routing.rejectedWithoutTenant()).isEqualTo(1);
    }

    @Test
    void codeThatOnlyCatchesSqlExceptionCannotSwallowTheRefusal() {
        // Boot's embedded-database probe (used to default spring.jpa.hibernate.ddl-auto) catches SQLException only.
        assertThatThrownBy(() -> EmbeddedDatabaseConnection.isEmbedded(routing))
                .isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void wrapperIntrospectionIsAnsweredWithoutRouting() throws SQLException {
        assertThat(routing.isWrapperFor(HikariDataSource.class)).isFalse();
        assertThat(routing.unwrap(TenantRoutingDataSource.class)).isSameAs(routing);
        assertThatThrownBy(() -> routing.unwrap(HikariDataSource.class)).isInstanceOf(SQLException.class);
        // Boot's metrics and health binding use this and swallow every exception (DataSourceUnwrapper#safeUnwrap).
        assertThat(DataSourceUnwrapper.unwrap(routing, HikariConfigMXBean.class, HikariDataSource.class))
                .isNull();
        assertThat(routing.rejectedWithoutTenant()).isZero();
    }

    @Test
    void anActiveTenantGetsItsOwnLazilyStartedPool() {
        DataSource target = TenantContext.call(ACME, routing::determineTargetDataSource);
        assertThat(target).isInstanceOfSatisfying(HikariDataSource.class, pool -> {
            assertThat(pool.getPoolName()).isEqualTo("tenant-acme");
            assertThat(pool.isRunning()).isFalse();
        });
        assertThat(TenantContext.call(ACME, routing::determineTargetDataSource)).isSameAs(target);
        assertThat(routing.openPools()).containsExactly(ACME);
    }

    @Test
    void anUnknownTenantIsRefusedWithoutAPool() {
        assertThatThrownBy(() -> TenantContext.run(UNKNOWN, routing::determineTargetDataSource))
                .isInstanceOf(TenantNotAvailableException.class)
                .hasMessageContaining("unknown tenant");
        assertThat(routing.openPools()).isEmpty();
    }

    @Test
    void aSuspendedTenantIsRefusedWithoutAPool() {
        assertThatThrownBy(() -> TenantContext.run(INITECH, routing::determineTargetDataSource))
                .isInstanceOf(TenantNotAvailableException.class)
                .hasMessageContaining("SUSPENDED");
        assertThat(routing.openPools()).isEmpty();
    }

    private record InMemoryDirectory(Map<TenantKey, TenantDescriptor> tenants) implements TenantDirectory {

        @Override
        public Optional<TenantDescriptor> find(TenantKey tenant) {
            return Optional.ofNullable(tenants.get(tenant));
        }

        @Override
        public List<TenantKey> activeTenants() {
            return tenants.values().stream()
                    .filter(tenant -> tenant.status() == TenantStatus.ACTIVE)
                    .map(TenantDescriptor::key)
                    .sorted(Comparator.comparing(TenantKey::value))
                    .toList();
        }
    }
}
