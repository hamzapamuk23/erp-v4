package com.smart.erp.spike.s1.tenancy;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.core.DefaultEventPublicationRegistry;
import org.springframework.modulith.events.core.EventPublicationRepository;

/**
 * Modulith's registry whose {@code destroy()} does not query: the stock one lists incomplete publications at shutdown,
 * a query with no tenant bound. Not final: Modulith's transactional methods need a CGLIB proxy.
 */
class TenantSafeEventPublicationRegistry extends DefaultEventPublicationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantSafeEventPublicationRegistry.class);

    TenantSafeEventPublicationRegistry(EventPublicationRepository events, Clock clock) {
        super(events, clock);
    }

    @Override
    public void destroy() {
        LOGGER.info("Shutting down; incomplete event publications stay in each tenant database and are resubmitted"
                + " by the tenant-walking republisher (ADR-0012)");
    }
}
