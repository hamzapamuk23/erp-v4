package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.UuidV7;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application layer (K3): one command, one transaction; the event is stored in the same tenant transaction. */
@Service
public class SampleService {

    private final SampleRecordRepository records;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    SampleService(SampleRecordRepository records, ApplicationEventPublisher events, Clock clock) {
        this.records = records;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UUID record(String name) {
        SampleRecord record = records.save(new SampleRecord(name));
        events.publishEvent(
                new SampleRecorded(UuidV7.next(), TenantContext.require(), record.getId(), clock.instant()));
        return record.getId();
    }
}
