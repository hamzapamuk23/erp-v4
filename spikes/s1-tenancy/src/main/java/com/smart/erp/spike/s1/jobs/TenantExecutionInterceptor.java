package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.event.ExecutionChain;
import com.github.kagkarlsson.scheduler.event.ExecutionInterceptor;
import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.ExecutionContext;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.smart.erp.spike.s1.kernel.TenantContext;

/**
 * Binds the job's tenant around its execution and clears it afterwards (doc §4.4 rule 6, ADR-0014). Platform jobs (no
 * tenant in their data) run with none.
 */
final class TenantExecutionInterceptor implements ExecutionInterceptor {

    @Override
    public CompletionHandler<?> execute(
            TaskInstance<?> taskInstance, ExecutionContext executionContext, ExecutionChain chain) {
        if (taskInstance.getData() instanceof TenantScopedTaskData data) {
            return TenantContext.call(data.tenant(), () -> chain.proceed(taskInstance, executionContext));
        }
        return chain.proceed(taskInstance, executionContext);
    }
}
