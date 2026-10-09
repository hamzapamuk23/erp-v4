package com.smart.erp.spike.s2.tenancy;

import com.smart.erp.spike.s2.kernel.TenantKey;

/**
 * One row of the platform DB's tenant registry. {@code (issuer, organizationAlias)} is the tenant's identity at the
 * identity provider (ADR-0005).
 */
public record TenantRecord(TenantKey key, TenantStatus status, String issuer, String organizationAlias) {}
