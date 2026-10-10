package com.smart.erp.spike.s2.tenancy;

/** Tenant lifecycle states (doc §4.3). Only ACTIVE tenants are served; the others get 503. */
public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    MAINTENANCE,
    ARCHIVED
}
