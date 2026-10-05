package com.smart.erp.spike.s1.kernel;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The tenant bound to the current thread (doc §4.4, §6.1). Binding is scope-only: {@link #run} and {@link #call} bind
 * for the duration of the action and restore the previous binding afterwards, so a pooled thread never keeps a stale
 * tenant. There is no setter and no default tenant.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantKey> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static Optional<TenantKey> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static TenantKey require() {
        TenantKey tenant = CURRENT.get();
        if (tenant == null) {
            throw new MissingTenantContextException();
        }
        return tenant;
    }

    public static void run(TenantKey tenant, Runnable action) {
        call(tenant, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs {@code action} with {@code tenant} bound. While a transaction is active only the tenant that is already bound
     * may be re-entered: the transaction's connection was taken for that tenant (doc §4.4 rule 7).
     */
    public static <T> T call(TenantKey tenant, Supplier<T> action) {
        Objects.requireNonNull(tenant, "tenant");
        TenantKey previous = CURRENT.get();
        if (TransactionSynchronizationManager.isActualTransactionActive() && !tenant.equals(previous)) {
            throw new TenantSwitchInTransactionException(previous, tenant);
        }
        CURRENT.set(tenant);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
