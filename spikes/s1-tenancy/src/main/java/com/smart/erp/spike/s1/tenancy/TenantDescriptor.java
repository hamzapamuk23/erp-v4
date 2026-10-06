package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantKey;

/** One row of the platform DB's tenant registry. */
public record TenantDescriptor(TenantKey key, String databaseName, TenantStatus status) {}
