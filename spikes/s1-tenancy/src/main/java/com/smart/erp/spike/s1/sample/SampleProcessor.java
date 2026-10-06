package com.smart.erp.spike.s1.sample;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleProcessor {

    private final SampleRecordRepository records;
    private final Clock clock;

    SampleProcessor(SampleRecordRepository records, Clock clock) {
        this.records = records;
        this.clock = clock;
    }

    @Transactional
    public void process(UUID sampleId) {
        records.findById(sampleId).ifPresent(record -> record.markProcessed(clock.instant()));
    }
}
