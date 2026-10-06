package com.smart.erp.spike.s1.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class TenantTaskDecoratorTests {

    private static final TenantKey ACME = new TenantKey("acme");

    // One reused worker thread: the setting in which a leaked tenant would show up.
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @BeforeEach
    void startExecutor() {
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(new TenantTaskDecorator());
        executor.initialize();
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdown();
    }

    @Test
    void theSubmittersTenantIsBoundOnTheWorker() throws Exception {
        Future<Optional<TenantKey>> seen = TenantContext.call(ACME, () -> executor.submit(TenantContext::current));
        assertThat(seen.get(5, TimeUnit.SECONDS)).contains(ACME);
    }

    @Test
    void theWorkerDoesNotKeepTheTenantForTheNextTask() throws Exception {
        TenantContext.call(ACME, () -> executor.submit(TenantContext::current)).get(5, TimeUnit.SECONDS);
        assertThat(executor.submit(TenantContext::current).get(5, TimeUnit.SECONDS))
                .isEmpty();
    }
}
