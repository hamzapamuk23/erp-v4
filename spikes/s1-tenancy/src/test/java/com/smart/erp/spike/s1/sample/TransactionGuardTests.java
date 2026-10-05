package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.kernel.TenantSwitchInTransactionException;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** doc §4.4 rule 7: the tenant is fixed before the transaction starts and cannot change inside it. */
@SpikeTest
class TransactionGuardTests {

    @Autowired
    SampleRecordRepository records;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    SampleQueries queries;

    @Test
    void switchingTenantInsideATransactionFailsAndRollsBack() {
        String name = "switch-" + UUID.randomUUID();

        TenantContext.run(
                ACME,
                () -> assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                            records.save(new SampleRecord(name));
                            TenantContext.run(GLOBEX, () -> records.save(new SampleRecord(name)));
                        }))
                        .isInstanceOf(TenantSwitchInTransactionException.class));

        assertThat(countByName(ACME, name)).isZero();
        assertThat(countByName(GLOBEX, name)).isZero();
    }

    @Test
    void reenteringTheSameTenantInsideATransactionIsAllowed() {
        String name = "reenter-" + UUID.randomUUID();

        TenantContext.run(
                ACME,
                () -> tx.executeWithoutResult(
                        status -> TenantContext.run(ACME, () -> records.save(new SampleRecord(name)))));

        assertThat(countByName(ACME, name)).isOne();
    }

    @Test
    void aTransactionCannotStartWithoutATenant() {
        assertRootCause(() -> tx.executeWithoutResult(status -> records.save(new SampleRecord("orphan"))))
                .isInstanceOf(MissingTenantContextException.class);
    }

    /**
     * PROPAGATION_SUPPORTS without an outer transaction: no actual transaction, but Spring binds the first connection to
     * the scope. Found by the final review: the first guard let this write go to acme's connection.
     */
    @Test
    void switchingTenantInsideASupportsScopeFailsInsteadOfWritingToTheFirstTenant() {
        String name = "supports-" + UUID.randomUUID();
        TransactionTemplate supports = scope(TransactionDefinition.PROPAGATION_SUPPORTS);

        TenantContext.run(
                ACME,
                () -> assertThatThrownBy(() -> supports.executeWithoutResult(status -> {
                            jdbc.sql("select 1").query(Integer.class).single(); // binds acme's connection to the scope
                            TenantContext.run(
                                    GLOBEX,
                                    () -> jdbc.sql(
                                                    "insert into sample.sample_record (id, version, name) values (gen_random_uuid(), 0, ?)")
                                            .param(name)
                                            .update());
                        }))
                        .isInstanceOf(TenantSwitchInTransactionException.class));

        assertThat(countByName(ACME, name)).isZero();
        assertThat(countByName(GLOBEX, name)).isZero();
    }

    @Test
    void jooqCannotReadAnotherTenantThroughANotSupportedScope() {
        String name = "not-supported-" + UUID.randomUUID();
        TenantContext.run(ACME, () -> tx.executeWithoutResult(status -> records.save(new SampleRecord(name))));
        TransactionTemplate notSupported = scope(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);

        TenantContext.run(
                ACME,
                () -> assertThatThrownBy(() -> notSupported.executeWithoutResult(status -> {
                            queries.countByName(name); // binds acme's connection to the scope
                            TenantContext.call(GLOBEX, () -> queries.countByName(name));
                        }))
                        .isInstanceOf(TenantSwitchInTransactionException.class));
    }

    private TransactionTemplate scope(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        return template;
    }

    private static long countByName(TenantKey tenant, String name) {
        return tenantDatabase(tenant)
                .sql("select count(*) from sample.sample_record where name = ?")
                .param(name)
                .query(Long.class)
                .single();
    }
}
