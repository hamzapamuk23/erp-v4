package com.smart.erp.spike.s1.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TenantContextTests {

    private static final TenantKey ACME = new TenantKey("acme");
    private static final TenantKey GLOBEX = new TenantKey("globex");

    @Test
    void nothingIsBoundOutsideAScope() {
        assertThat(TenantContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::require).isInstanceOf(MissingTenantContextException.class);
    }

    @Test
    void aScopeBindsTheTenantOnlyForItsDuration() {
        assertThat(TenantContext.call(ACME, TenantContext::require)).isEqualTo(ACME);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void nestedScopesRestoreTheOuterTenant() {
        TenantContext.run(ACME, () -> {
            assertThat(TenantContext.call(GLOBEX, TenantContext::require)).isEqualTo(GLOBEX);
            assertThat(TenantContext.require()).isEqualTo(ACME);
        });
    }

    @Test
    void aFailingActionStillClearsTheScope() {
        assertThatThrownBy(() -> TenantContext.run(ACME, () -> {
                    throw new IllegalStateException("boom");
                }))
                .hasMessage("boom");
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void theBindingIsNotVisibleToOtherThreads() {
        AtomicReference<Optional<TenantKey>> seen = new AtomicReference<>();
        TenantContext.run(ACME, () -> join(Thread.ofVirtual().start(() -> seen.set(TenantContext.current()))));
        assertThat(seen.get()).isEmpty();
    }

    @Test
    void switchingTenantInsideATransactionIsRejected() {
        TenantContext.run(
                ACME,
                () -> inTransaction(() -> assertThatThrownBy(() -> TenantContext.run(GLOBEX, () -> {}))
                        .isInstanceOf(TenantSwitchInTransactionException.class)
                        .hasMessageContaining("from acme to globex")));
    }

    @Test
    void reenteringTheBoundTenantInsideATransactionIsAllowed() {
        TenantContext.run(
                ACME,
                () -> inTransaction(() -> assertThat(TenantContext.call(ACME, TenantContext::require))
                        .isEqualTo(ACME)));
    }

    @Test
    void bindingATenantAfterTheTransactionStartedIsRejected() {
        inTransaction(() -> assertThatThrownBy(() -> TenantContext.run(ACME, () -> {}))
                .isInstanceOf(TenantSwitchInTransactionException.class)
                .hasMessageContaining("before the transaction starts"));
    }

    /** Marks a transaction active the way Spring's transaction managers do, without a database. */
    private static void inTransaction(Runnable action) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            action.run();
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
