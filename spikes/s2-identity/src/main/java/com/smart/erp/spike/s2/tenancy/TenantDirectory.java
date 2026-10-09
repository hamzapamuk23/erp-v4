package com.smart.erp.spike.s2.tenancy;

import com.smart.erp.spike.s2.kernel.TenantKey;
import java.util.Optional;

/** The tenant registry (doc §4.3): resolves a tenant from a host or from an identity-provider identity. */
public interface TenantDirectory {

    /** Resolves the tenant that owns {@code normalizedHost}, a host that went through {@link TenantHost#normalize}. */
    Optional<TenantRecord> findByHost(String normalizedHost);

    /** Resolves a tenant from the identity provider's issuer and the organization alias (ADR-0005). */
    Optional<TenantRecord> findByIdentity(String issuer, String organizationAlias);

    /** Returns the registered tenant; throws {@link IllegalStateException} when there is no such tenant. */
    TenantRecord require(TenantKey key);
}
