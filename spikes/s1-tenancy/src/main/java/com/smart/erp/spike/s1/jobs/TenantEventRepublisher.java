package com.smart.erp.spike.s1.jobs;

import com.smart.erp.spike.s1.kernel.TenantContext;
import com.smart.erp.spike.s1.kernel.TenantKey;
import com.smart.erp.spike.s1.tenancy.TenantDirectory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Component;

/**
 * Resubmits incomplete event publications tenant by tenant (ADR-0012): Modulith's own start-up republish has no
 * tenant. A failing tenant is reported and skipped; the others continue. Spike simplification: it walks every ACTIVE
 * tenant, which opens a pool per tenant per run; doc §6.12 asks for an activity index instead (Phase 2).
 */
@Component
public class TenantEventRepublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantEventRepublisher.class);

    private final TenantDirectory directory;
    private final IncompleteEventPublications publications;

    TenantEventRepublisher(TenantDirectory directory, IncompleteEventPublications publications) {
        this.directory = directory;
        this.publications = publications;
    }

    public RepublishReport republishAll(Duration minAge) {
        List<TenantKey> republished = new ArrayList<>();
        Map<TenantKey, String> failed = new LinkedHashMap<>();
        for (TenantKey tenant : directory.activeTenants()) {
            try {
                TenantContext.run(tenant, () -> publications.resubmitIncompletePublicationsOlderThan(minAge));
                republished.add(tenant);
            } catch (RuntimeException e) {
                String cause = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
                LOGGER.warn("Republishing incomplete event publications failed for tenant {}: {}", tenant, cause, e);
                failed.put(tenant, cause);
            }
        }
        return new RepublishReport(List.copyOf(republished), Map.copyOf(failed));
    }
}
