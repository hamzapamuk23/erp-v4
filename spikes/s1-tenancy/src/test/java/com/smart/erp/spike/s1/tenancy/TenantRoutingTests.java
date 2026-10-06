package com.smart.erp.spike.s1.tenancy;

import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.kernel.UuidV7;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpikeTest
class TenantRoutingTests {

    /** Boot's JdbcClient: built on the single default DataSource candidate, the routing one. */
    @Autowired
    JdbcClient jdbc;

    @Test
    void aWriteLandsInTheBoundTenantsDatabaseOnly() {
        String name = "routing-" + UUID.randomUUID();
        TenantContext.run(ACME, () -> insert(name));

        assertThat(countByName(tenantDatabase(ACME), name)).isOne();
        assertThat(countByName(tenantDatabase(GLOBEX), name)).isZero();
    }

    @Test
    void eachTenantSeesOnlyItsOwnRows() {
        String name = "visible-" + UUID.randomUUID();
        TenantContext.run(GLOBEX, () -> insert(name));

        assertThat(TenantContext.call(GLOBEX, () -> countByName(jdbc, name))).isOne();
        assertThat(TenantContext.call(ACME, () -> countByName(jdbc, name))).isZero();
    }

    @Test
    void accessWithoutATenantFails() {
        assertRootCause(() -> countByName(jdbc, "anything")).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void aSuspendedTenantIsRefused() {
        assertRootCause(() -> TenantContext.call(INITECH, () -> countByName(jdbc, "anything")))
                .isInstanceOf(TenantNotAvailableException.class);
    }

    @Test
    void anUnknownTenantIsRefused() {
        assertRootCause(() -> TenantContext.call(new TenantKey("nobody"), () -> countByName(jdbc, "anything")))
                .isInstanceOf(TenantNotAvailableException.class);
    }

    private void insert(String name) {
        jdbc.sql("insert into sample.sample_record (id, version, name) values (?, 0, ?)")
                .param(UuidV7.next())
                .param(name)
                .update();
    }

    private static long countByName(JdbcClient client, String name) {
        return client.sql("select count(*) from sample.sample_record where name = ?")
                .param(name)
                .query(Long.class)
                .single();
    }
}
