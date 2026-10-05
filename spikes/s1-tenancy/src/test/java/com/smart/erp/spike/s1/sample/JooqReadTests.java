package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.support.SpikeTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.ExecuteListenerProvider;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@SpikeTest
class JooqReadTests {

    @Autowired
    DSLContext dsl;

    @Autowired
    SampleQueries queries;

    @Autowired
    SampleRecordRepository records;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate tx;

    @Test
    void theDialectIsConfiguredNotDetected() {
        assertThat(dsl.dialect()).isEqualTo(SQLDialect.POSTGRES);
    }

    @Test
    void readsOnlyTheBoundTenantsRows() {
        String name = "jooq-" + UUID.randomUUID();
        TenantContext.run(ACME, () -> tx.executeWithoutResult(status -> records.save(new SampleRecord(name))));

        assertThat(TenantContext.call(ACME, () -> queries.countByName(name))).isOne();
        assertThat(TenantContext.call(GLOBEX, () -> queries.countByName(name))).isZero();
    }

    /** The trap of doc §4.5 / §7.7: jOOQ bypasses Hibernate, so an unflushed persist is invisible to it. */
    @Test
    void withoutAFlushJooqDoesNotSeeTheTransactionsOwnJpaWrites() {
        String name = "unflushed-" + UUID.randomUUID();
        DSLContext withoutListeners = DSL.using(dsl.configuration().derive(new ExecuteListenerProvider[0]));

        TenantContext.run(
                ACME,
                () -> tx.executeWithoutResult(status -> {
                    records.save(new SampleRecord(name));
                    assertThat(withoutListeners.fetchCount(SampleQueries.SAMPLE_RECORD, SampleQueries.NAME.eq(name)))
                            .isZero();
                    entityManager.flush();
                    assertThat(withoutListeners.fetchCount(SampleQueries.SAMPLE_RECORD, SampleQueries.NAME.eq(name)))
                            .isOne();
                    status.setRollbackOnly();
                }));
    }

    @Test
    void theFlushListenerMakesUnflushedWritesVisibleAutomatically() {
        String name = "auto-flush-" + UUID.randomUUID();

        TenantContext.run(
                ACME,
                () -> tx.executeWithoutResult(status -> {
                    records.save(new SampleRecord(name));
                    assertThat(queries.countByName(name)).isOne();
                    status.setRollbackOnly();
                }));
    }

    @Test
    void jooqWithoutATenantFails() {
        assertRootCause(() -> queries.countByName("anything")).isInstanceOf(MissingTenantContextException.class);
    }
}
