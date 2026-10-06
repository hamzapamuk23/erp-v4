package com.smart.erp.spike.s1.jobs;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GHOST;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.INITECH;
import static com.smart.erp.spike.s1.support.SpikeDatabases.platformDatabase;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.sample.SampleRecorded;
import com.smart.erp.spike.s1.sample.SampleService;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;

@SpikeTest
@Import(TenantEventRepublisherTests.FlakyListenerConfiguration.class)
class TenantEventRepublisherTests {

    @Autowired
    SampleService samples;

    @Autowired
    FlakyListener flaky;

    @Autowired
    TenantEventRepublisher republisher;

    @Autowired
    SchedulerClient scheduler;

    @Test
    void aFailedPublicationIsResubmittedInItsOwnTenant() {
        flaky.failNextInvocation();
        UUID sampleId = TenantContext.call(ACME, () -> samples.record("flaky-" + UUID.randomUUID()));
        await().untilAsserted(
                        () -> assertThat(flakyPublications(ACME, sampleId)).containsExactly("FAILED"));

        RepublishReport report = republisher.republishAll(Duration.ZERO);

        assertThat(report.republished()).contains(ACME);
        await().untilAsserted(
                        () -> assertThat(flaky.successfulTenantFor(sampleId)).contains(ACME));
        await().untilAsserted(
                        () -> assertThat(flakyPublications(ACME, sampleId)).isEmpty());
        assertThat(flakyPublications(GLOBEX, sampleId)).isEmpty();
    }

    @Test
    void anUnreachableTenantIsReportedAndDoesNotStopTheOthers() {
        RepublishReport report = republisher.republishAll(Duration.ZERO);

        assertThat(report.republished()).containsExactly(ACME, GLOBEX).doesNotContain(INITECH);
        assertThat(report.failed()).containsOnlyKeys(GHOST);
        assertThat(report.failed().get(GHOST)).contains("erp_t_ghost");
    }

    /**
     * The same republishAll runs as a db-scheduler platform job: not at start-up (no run recorded although several
     * contexts have started), but when its execution comes due; it completes despite the unreachable tenant.
     */
    @Test
    void theRepublisherRunsAsARecurringPlatformJobButNotAtStartup() {
        assertThat(republisherColumn("last_success")).as("no run at start-up").isEmpty();
        assertThat(republisherColumn("execution_time"))
                .hasValueSatisfying(
                        next -> assertThat(next).isAfter(OffsetDateTime.now().plusMinutes(30)));

        assertThat(scheduler.reschedule(
                        TaskInstanceId.of(JobsConfiguration.EVENT_REPUBLISHER, RecurringTask.INSTANCE), Instant.now()))
                .isTrue();

        await().untilAsserted(
                        () -> assertThat(republisherColumn("last_success")).isPresent());
        assertThat(republisherColumn("execution_time"))
                .as("the next run is one interval after this one")
                .hasValueSatisfying(
                        next -> assertThat(next).isAfter(OffsetDateTime.now().plusMinutes(30)));
    }

    private static Optional<OffsetDateTime> republisherColumn(String column) {
        return platformDatabase()
                .sql("select " + column + " from scheduled_tasks where task_name = ? and task_instance = ?")
                .param(JobsConfiguration.EVENT_REPUBLISHER)
                .param(RecurringTask.INSTANCE)
                .query(OffsetDateTime.class)
                .optional();
    }

    private static List<String> flakyPublications(TenantKey tenant, UUID sampleId) {
        return tenantDatabase(tenant)
                .sql("""
                        select status from platform_events.event_publication
                         where listener_id like '%FlakyListener%' and serialized_event like ?
                        """)
                .param("%" + sampleId + "%")
                .query(String.class)
                .list();
    }

    /** Fails once on demand; accessed through methods because the bean is a CGLIB proxy. */
    static class FlakyListener {

        private final AtomicBoolean failNext = new AtomicBoolean();
        private final Map<UUID, TenantKey> successes = new ConcurrentHashMap<>();

        public void failNextInvocation() {
            failNext.set(true);
        }

        public Optional<TenantKey> successfulTenantFor(UUID sampleId) {
            return Optional.ofNullable(successes.get(sampleId));
        }

        @ApplicationModuleListener
        void on(SampleRecorded event) {
            if (failNext.compareAndSet(true, false)) {
                throw new IllegalStateException("Simulated listener failure");
            }
            successes.put(event.sampleId(), TenantContext.require());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FlakyListenerConfiguration {

        @Bean
        FlakyListener flakyListener() {
            return new FlakyListener();
        }
    }
}
