package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;

/** The bound tenant is not in the registry or is not ACTIVE; no pool is opened for it. */
public class TenantNotAvailableException extends IllegalStateException {

    private final transient TenantKey tenant;

    public TenantNotAvailableException(TenantKey tenant, String reason) {
        super("Tenant " + tenant + " is not available: " + reason);
        this.tenant = tenant;
    }

    public TenantKey tenant() {
        return tenant;
    }
}
