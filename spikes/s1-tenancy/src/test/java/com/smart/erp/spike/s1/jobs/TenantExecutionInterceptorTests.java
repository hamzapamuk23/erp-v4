package com.smart.erp.spike.s1.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.kagkarlsson.scheduler.event.ExecutionChain;
import com.github.kagkarlsson.scheduler.task.ExecutionHandler;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Pins why a tenant job runs where it does: the interceptor, not the job, binds the tenant. */
class TenantExecutionInterceptorTests {

    private static final TenantKey INITECH = new TenantKey("initech");

    private final AtomicReference<Optional<TenantKey>> seenByJob = new AtomicReference<>();

    @Test
    void theTenantOfTheTaskDataIsBoundOnlyAroundTheExecution() {
        run(new TaskInstance<>("job", "1", new TenantData(INITECH)));

        assertThat(seenByJob.get()).contains(INITECH);
        assertThat(TenantContext.current()).isEmpty();
    }

    @Test
    void aPlatformTaskRunsWithoutATenant() {
        run(new TaskInstance<>("job", "1", "platform data"));

        assertThat(seenByJob.get()).isEmpty();
    }

    private void run(TaskInstance<?> instance) {
        ExecutionHandler<Object> job = (taskInstance, context) -> {
            seenByJob.set(TenantContext.current());
            return null;
        };
        new ExecutionChain(List.of(new TenantExecutionInterceptor()), job).proceed(instance, null);
    }

    private record TenantData(TenantKey tenant) implements TenantScopedTaskData {}
}
