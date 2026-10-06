package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.List;
import java.util.Optional;

/** The tenant registry (doc §4.3). */
public interface TenantDirectory {

    Optional<TenantDescriptor> find(TenantKey tenant);

    /** ACTIVE tenants, ordered by key. */
    List<TenantKey> activeTenants();
}
