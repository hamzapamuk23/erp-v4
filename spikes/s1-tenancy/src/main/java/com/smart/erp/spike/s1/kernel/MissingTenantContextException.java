package com.smart.erp.spike.s1.kernel;

/**
 * Tenant database access without a bound tenant (doc §4.4: there is no default tenant). Unchecked on purpose: code that
 * only catches {@link java.sql.SQLException} cannot swallow it.
 */
public class MissingTenantContextException extends IllegalStateException {

    public MissingTenantContextException() {
        super("No tenant is bound to this thread; tenant database access must run inside TenantContext.run/call"
                + " (doc §4.4)");
    }
}
