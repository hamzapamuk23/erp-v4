package com.smart.erp.spike.s1.sample;

import static com.smart.erp.spike.s1.support.SpikeDatabases.ACME;
import static com.smart.erp.spike.s1.support.SpikeDatabases.GLOBEX;
import static com.smart.erp.spike.s1.support.SpikeDatabases.tenantDatabase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.support.SpikeTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

@SpikeTest
@Import(EventPublicationTests.RecordingListenerConfiguration.class)
class EventPublicationTests {

    @Autowired
    SampleService samples;

    @Autowired
    RecordingListener listener;

    @Autowired
    TransactionTemplate tx;

    @Test
    void thePublicationIsKeptInThePublishersTenantDatabaseUntilTheListenerCompletes() {
        CountDownLatch gate = listener.holdNextInvocation();
        UUID sampleId = TenantContext.call(ACME, () -> samples.record("event-" + UUID.randomUUID()));
        try {
            await().untilAsserted(() ->
                    assertThat(recordingPublications(ACME, sampleId)).containsExactly("PROCESSING"));
            assertThat(recordingPublications(GLOBEX, sampleId)).isEmpty();
        } finally {
            gate.countDown();
        }
        // completion-mode: delete
        await().untilAsserted(
                        () -> assertThat(recordingPublications(ACME, sampleId)).isEmpty());
    }

    @Test
    void theListenerRunsAsynchronouslyInThePublishersTenant() {
        long testThread = Thread.currentThread().threadId();
        UUID sampleId = TenantContext.call(GLOBEX, () -> samples.record("event-" + UUID.randomUUID()));

        await().untilAsserted(() -> assertThat(listener.invocationFor(sampleId)).hasValueSatisfying(invocation -> {
            assertThat(invocation.tenant()).isEqualTo(GLOBEX);
            assertThat(invocation.threadId()).isNotEqualTo(testThread);
        }));
    }

    @Test
    void aRolledBackTransactionPublishesNothing() {
        UUID sampleId = TenantContext.call(
                ACME,
                () -> tx.execute(status -> {
                    UUID id = samples.record("rolled-back-" + UUID.randomUUID());
                    status.setRollbackOnly();
                    return id;
                }));

        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(2))
                .untilAsserted(
                        () -> assertThat(listener.invocationFor(sampleId)).isEmpty());
        assertThat(recordingPublications(ACME, sampleId)).isEmpty();
    }

    private static List<String> recordingPublications(TenantKey tenant, UUID sampleId) {
        return tenantDatabase(tenant)
                .sql("""
                        select status from platform_events.event_publication
                         where listener_id like '%RecordingListener%' and serialized_event like ?
                        """)
                .param("%" + sampleId + "%")
                .query(String.class)
                .list();
    }

    record Invocation(TenantKey tenant, long threadId) {}

    /** Test-only listener; accessed through methods because the bean is a CGLIB proxy. */
    static class RecordingListener {

        private final Map<UUID, Invocation> invocations = new ConcurrentHashMap<>();
        private final AtomicReference<CountDownLatch> gate = new AtomicReference<>();

        public CountDownLatch holdNextInvocation() {
            CountDownLatch latch = new CountDownLatch(1);
            gate.set(latch);
            return latch;
        }

        public Optional<Invocation> invocationFor(UUID sampleId) {
            return Optional.ofNullable(invocations.get(sampleId));
        }

        @ApplicationModuleListener
        void on(SampleRecorded event) throws InterruptedException {
            CountDownLatch latch = gate.getAndSet(null);
            if (latch != null && !latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The test never opened the gate");
            }
            invocations.put(
                    event.sampleId(),
                    new Invocation(
                            TenantContext.current().orElse(null),
                            Thread.currentThread().threadId()));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingListenerConfiguration {

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }
}
