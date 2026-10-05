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
import org.springframework.transaction.support.TransactionTemplate;

/** doc §4.4 rule 7: the tenant is fixed before the transaction starts and cannot change inside it. */
@SpikeTest
class TransactionGuardTests {

    @Autowired
    SampleRecordRepository records;

    @Autowired
    TransactionTemplate tx;

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

    private static long countByName(TenantKey tenant, String name) {
        return tenantDatabase(tenant)
                .sql("select count(*) from sample.sample_record where name = ?")
                .param(name)
                .query(Long.class)
                .single();
    }
}
