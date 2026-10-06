package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.kernel.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sample_record", schema = "sample")
public class SampleRecord {

    /** Assigned at construction (ADR-0013): known to events and idempotency keys before anything is written. */
    @Id
    private UUID id;

    /** A wrapper type on purpose: Spring Data treats a null version as new and persists instead of merging. */
    @Version
    private Long version;

    @Column(nullable = false)
    private String name;

    private Instant processedAt;

    protected SampleRecord() {}

    public SampleRecord(String name) {
        this.id = UuidV7.next();
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    /** Idempotent: a redelivered job does not move the timestamp. */
    void markProcessed(Instant at) {
        if (processedAt == null) {
            processedAt = at;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SampleRecord that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
