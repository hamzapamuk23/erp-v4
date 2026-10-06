package com.smart.erp.spike.s1.sample;

import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SampleJobs {

    static final String PROCESS_SAMPLE = "sample.process-sample";

    /** Runs with the tenant of its data bound (TenantExecutionInterceptor). Failures retry after 5 minutes. */
    @Bean
    OneTimeTask<ProcessSampleData> processSampleTask(SampleProcessor processor) {
        return Tasks.oneTime(PROCESS_SAMPLE, ProcessSampleData.class)
                .execute((instance, context) ->
                        processor.process(instance.getData().sampleId()));
    }
}
