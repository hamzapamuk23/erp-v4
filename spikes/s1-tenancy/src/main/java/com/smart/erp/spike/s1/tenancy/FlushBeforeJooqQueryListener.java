package com.smart.erp.spike.s1.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * doc §7.7 rule 7, made automatic: before a jOOQ statement runs inside a read-write JPA transaction, the transaction's
 * pending JPA writes are flushed, so jOOQ sees them. Like Hibernate's own auto-flush before native queries. Uses only
 * an EntityManager that is already bound; never creates one.
 */
final class FlushBeforeJooqQueryListener implements ExecuteListener {

    private final transient EntityManagerFactory entityManagerFactory;

    FlushBeforeJooqQueryListener(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void start(ExecuteContext ctx) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return;
        }
        if (TransactionSynchronizationManager.getResource(entityManagerFactory) instanceof EntityManagerHolder holder) {
            holder.getEntityManager().flush();
        }
    }
}
