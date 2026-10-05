package com.smart.erp.spike.s1.tenancy;

/** Tenant lifecycle states (doc §4.3). Only ACTIVE tenants get a pool. */
public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    MAINTENANCE,
    ARCHIVED
}
