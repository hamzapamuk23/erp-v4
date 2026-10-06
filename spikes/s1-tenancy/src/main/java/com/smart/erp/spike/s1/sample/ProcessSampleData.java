package com.smart.erp.spike.s1.sample;

import com.smart.erp.spike.s1.jobs.TenantScopedTaskData;
import com.smart.erp.spike.s1.kernel.TenantKey;
import java.util.UUID;

/** Serialized as {@code {"tenantKey":"…","sampleId":"…"}}; {@link #tenant()} is not a bean property. */
public record ProcessSampleData(String tenantKey, UUID sampleId) implements TenantScopedTaskData {

    @Override
    public TenantKey tenant() {
        return new TenantKey(tenantKey);
    }
}
