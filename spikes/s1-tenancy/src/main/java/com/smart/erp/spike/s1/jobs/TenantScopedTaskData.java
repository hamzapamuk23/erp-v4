package com.smart.erp.spike.s1.jobs;

import com.smart.erp.spike.s1.kernel.TenantKey;

/** Task data of a tenant job (doc §6.12): the job runs with this tenant bound, nothing else. */
public interface TenantScopedTaskData {

    TenantKey tenant();
}
