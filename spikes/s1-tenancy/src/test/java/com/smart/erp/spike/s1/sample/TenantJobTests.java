package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static com.smart.erp.spike.s1.support.SpikeDatabases.platformDatabase;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.UuidV7;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@SpikeTest
class TenantJobTests {

    @Autowired
    SampleService samples;

    @Autowired
    SampleQueries queries;

    @Autowired
    SchedulerClient scheduler;

    @Autowired
    OneTimeTask<ProcessSampleData> processSample;

    @Test
    void recordingASampleRunsItsJobInTheSameTenant() {
        UUID sampleId = TenantContext.call(ACME, () -> samples.record("job-" + UUID.randomUUID()));

        await().untilAsserted(() -> assertThat(TenantContext.call(ACME, () -> queries.processedAt(sampleId)))
                .isPresent());
        assertThat(tenantDatabase(GLOBEX)
                        .sql("select count(*) from sample.sample_record where id = ?")
                        .param(sampleId)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void theEventIdIsTheJobInstanceIdSoARedeliveryIsDropped() {
        String eventId = UuidV7.next().toString();
        ProcessSampleData data = new ProcessSampleData(GLOBEX.value(), UuidV7.next());
        Instant later = Instant.now().plus(Duration.ofHours(1));
        try {
            assertThat(scheduler.scheduleIfNotExists(processSample.instance(eventId, data), later))
                    .isTrue();
            assertThat(scheduler.scheduleIfNotExists(processSample.instance(eventId, data), later))
                    .isFalse();
            assertThat(scheduledRows(eventId)).isOne();
        } finally {
            scheduler.cancel(TaskInstanceId.of(SampleJobs.PROCESS_SAMPLE, eventId));
        }
    }

    @Test
    void taskDataIsJsonThatNamesTheTenant() {
        String eventId = UuidV7.next().toString();
        scheduler.scheduleIfNotExists(
                processSample.instance(eventId, new ProcessSampleData(GLOBEX.value(), UuidV7.next())),
                Instant.now().plus(Duration.ofHours(1)));
        try {
            byte[] taskData = platformDatabase()
                    .sql("select task_data from scheduled_tasks where task_name = ? and task_instance = ?")
                    .param(SampleJobs.PROCESS_SAMPLE)
                    .param(eventId)
                    .query(byte[].class)
                    .single();
            assertThat(new String(taskData, StandardCharsets.UTF_8)).contains("\"tenantKey\":\"globex\"");
        } finally {
            scheduler.cancel(TaskInstanceId.of(SampleJobs.PROCESS_SAMPLE, eventId));
        }
    }

    @Test
    void aJobForASuspendedTenantFailsVisiblyAndWaitsForARetry() {
        String eventId = UuidV7.next().toString();
        scheduler.scheduleIfNotExists(
                processSample.instance(eventId, new ProcessSampleData(INITECH.value(), UuidV7.next())), Instant.now());
        try {
            await().untilAsserted(() -> assertThat(platformDatabase()
                            .sql("select consecutive_failures from scheduled_tasks"
                                    + " where task_name = ? and task_instance = ?")
                            .param(SampleJobs.PROCESS_SAMPLE)
                            .param(eventId)
                            .query(Integer.class)
                            .optional())
                    .hasValue(1));
        } finally {
            scheduler.cancel(TaskInstanceId.of(SampleJobs.PROCESS_SAMPLE, eventId));
        }
    }

    private static long scheduledRows(String eventId) {
        return platformDatabase()
                .sql("select count(*) from scheduled_tasks where task_name = ? and task_instance = ?")
                .param(SampleJobs.PROCESS_SAMPLE)
                .param(eventId)
                .query(Long.class)
                .single();
    }
}
