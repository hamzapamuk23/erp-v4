package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeAssertions.assertRootCause;
import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.MissingTenantContextException;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@SpikeTest
class SamplePersistenceTests {

    @Autowired
    SampleRecordRepository records;

    @Autowired
    TransactionTemplate tx;

    @Test
    void theIdIsAUuidV7KnownBeforePersist() {
        assertThat(new SampleRecord("uuid-" + UUID.randomUUID()).getId().version())
                .isEqualTo(7);
    }

    @Test
    void aNewRecordWithAnAssignedIdIsPersistedNotMerged() {
        SampleRecord record = new SampleRecord("persist-" + UUID.randomUUID());

        SampleRecord saved = TenantContext.call(ACME, () -> tx.execute(status -> records.save(record)));

        // merge() would have issued a SELECT and returned a copy; persist() keeps the instance (ADR-0013).
        assertThat(saved).isSameAs(record);
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    void theRowLandsInTheBoundTenantsDatabaseOnly() {
        SampleRecord record = new SampleRecord("jpa-" + UUID.randomUUID());

        TenantContext.run(GLOBEX, () -> tx.executeWithoutResult(status -> records.save(record)));

        assertThat(countById(GLOBEX, record.getId())).isOne();
        assertThat(countById(ACME, record.getId())).isZero();
    }

    @Test
    void repositoryAccessWithoutATenantFails() {
        assertRootCause(() -> records.count()).isInstanceOf(MissingTenantContextException.class);
    }

    private static long countById(TenantKey tenant, UUID id) {
        return tenantDatabase(tenant)
                .sql("select count(*) from sample.sample_record where id = ?")
                .param(id)
                .query(Long.class)
                .single();
    }
}
