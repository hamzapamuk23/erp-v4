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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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

    /**
     * The event-id deduplication only lasts while the job row exists: a one-time task's row is removed on completion,
     * so a redelivery after that (doc §4.4: lost completion record, then republish) schedules and runs the job again.
     * Idempotent jobs are the actual guarantee (ADR-0014 "İşler idempotent yazılır").
     */
    @Test
    void aRedeliveryAfterTheJobCompletedRunsItAgainSoJobsMustBeIdempotent() {
        UUID sampleId = TenantContext.call(ACME, () -> samples.record("redelivered-" + UUID.randomUUID()));
        await().untilAsserted(() -> assertThat(TenantContext.call(ACME, () -> queries.processedAt(sampleId)))
                .isPresent());
        Instant firstRun =
                TenantContext.call(ACME, () -> queries.processedAt(sampleId)).orElseThrow();
        String eventId = UuidV7.next().toString();
        ProcessSampleData data = new ProcessSampleData(ACME.value(), sampleId);

        assertThat(scheduler.scheduleIfNotExists(processSample.instance(eventId, data), Instant.now()))
                .isTrue();
        await().untilAsserted(() ->
                assertThat(scheduledRows(eventId)).as("completed and removed").isZero());
        assertThat(scheduler.scheduleIfNotExists(processSample.instance(eventId, data), Instant.now()))
                .as("same event id, but the row is gone")
                .isTrue();
        await().untilAsserted(() -> assertThat(scheduledRows(eventId)).isZero());

        assertThat(TenantContext.call(ACME, () -> queries.processedAt(sampleId)))
                .contains(firstRun);
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
            Map<String, Object> row = platformDatabase()
                    .sql("select last_failure, execution_time from scheduled_tasks"
                            + " where task_name = ? and task_instance = ?")
                    .param(SampleJobs.PROCESS_SAMPLE)
                    .param(eventId)
                    .query()
                    .singleRow();
            assertThat(row.get("last_failure")).as("the failure is recorded").isNotNull();
            // OneTimeTask's default failure handler retries five minutes later (db-scheduler 16.12.0).
            assertThat(((Timestamp) row.get("execution_time")).toInstant())
                    .isBetween(
                            Instant.now().plus(Duration.ofMinutes(4)),
                            Instant.now().plus(Duration.ofMinutes(6)));
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
