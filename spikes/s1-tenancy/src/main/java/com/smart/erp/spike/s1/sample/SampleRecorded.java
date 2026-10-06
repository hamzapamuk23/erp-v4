package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.kernel.TenantKey;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event (doc §5.4): past tense, ids and minimal data, carries its tenant. Spike subset: no companyId and no
 * schemaVersion.
 */
public record SampleRecorded(UUID eventId, TenantKey tenantKey, UUID sampleId, Instant occurredAt) {}
