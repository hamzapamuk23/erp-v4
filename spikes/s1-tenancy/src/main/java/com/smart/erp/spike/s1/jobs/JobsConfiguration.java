package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.smart.erp.spike.s1.tenancy.PlatformDb;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class JobsConfiguration {

    static final String EVENT_REPUBLISHER = "platform.event-republisher";

    /** db-scheduler on the platform DB (ADR-0014); the task queue never lives in a tenant DB. */
    @Bean
    Scheduler scheduler(
            @PlatformDb DataSource platformDataSource,
            ObjectProvider<OneTimeTask<?>> oneTimeTasks,
            ObjectProvider<RecurringTask<?>> recurringTasks,
            JsonMapper jsonMapper,
            JobsProperties properties) {
        List<Task<?>> knownTasks = new ArrayList<>(oneTimeTasks.orderedStream().toList());
        return Scheduler.create(platformDataSource, knownTasks)
                .startTasks(recurringTasks.orderedStream().toList())
                .serializer(new JsonTaskDataSerializer(jsonMapper))
                .addExecutionInterceptor(new TenantExecutionInterceptor())
                .pollingInterval(properties.pollingInterval())
                .threads(properties.threads())
                .shutdownMaxWait(properties.shutdownMaxWait())
                .enableImmediateExecution()
                .build();
    }

    /** Polls only after the context is up and stops before the DataSources close (last to start, first to stop). */
    @Bean
    SmartLifecycle schedulerLifecycle(Scheduler scheduler) {
        return new SmartLifecycle() {

            private volatile boolean running;

            @Override
            public void start() {
                scheduler.start();
                running = true;
            }

            @Override
            public void stop() {
                scheduler.stop();
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }
        };
    }

    /** A platform job: no tenant in its data, it binds each tenant itself (ADR-0012, ADR-0014). */
    @Bean
    RecurringTask<Void> eventRepublisherTask(TenantEventRepublisher republisher, EventRepublishProperties properties) {
        return Tasks.recurring(EVENT_REPUBLISHER, new DelayedFixedDelay(properties.interval()))
                .execute((instance, context) -> republisher.republishAll(properties.minAge()));
    }
}
