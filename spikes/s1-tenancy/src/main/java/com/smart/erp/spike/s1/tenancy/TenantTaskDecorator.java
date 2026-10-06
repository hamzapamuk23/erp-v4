package com.smart.erp.spike.s1.tenancy;

import com.smart.erp.spike.s1.kernel.TenantContext;
import org.springframework.core.task.TaskDecorator;

/**
 * Carries the submitter's tenant to the worker thread (doc §4.4 rule 4). The worker gets the binding only for the
 * task's duration; with no tenant at submit time the task runs without one.
 */
public final class TenantTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return TenantContext.current()
                .<Runnable>map(tenant -> () -> TenantContext.run(tenant, runnable))
                .orElse(runnable);
    }
}
