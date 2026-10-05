package com.smart.erp.spike.s1.sample;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.smart.erp.spike.s1.kernel.TenantContext;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Enqueues the follow-up job after the tenant transaction committed (doc §6.12 "transaction ile kuyruğa alma"). The
 * job instance id is the event id, so a redelivered event schedules nothing new.
 */
@Component
class SampleRecordedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleRecordedListener.class);

    private final SchedulerClient scheduler;
    private final OneTimeTask<ProcessSampleData> processSample;
    private final Clock clock;

    SampleRecordedListener(SchedulerClient scheduler, OneTimeTask<ProcessSampleData> processSample, Clock clock) {
        this.scheduler = scheduler;
        this.processSample = processSample;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(SampleRecorded event) {
        if (!event.tenantKey().equals(TenantContext.require())) {
            throw new IllegalStateException(
                    "Event of tenant " + event.tenantKey() + " delivered under " + TenantContext.require());
        }
        boolean scheduled = scheduler.scheduleIfNotExists(
                processSample.instance(
                        event.eventId().toString(),
                        new ProcessSampleData(event.tenantKey().value(), event.sampleId())),
                clock.instant());
        if (!scheduled) {
            LOGGER.debug("Job for event {} already scheduled (redelivery)", event.eventId());
        }
    }
}
