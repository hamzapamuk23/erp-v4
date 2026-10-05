package com.smart.erp.spike.s1.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.hibernate.Incubating;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.junit.jupiter.api.Test;

/**
 * Learning test for the Phase 0 check "UUIDv7 generation in Hibernate 7" (ADR-0013). Facts only; the decision and its
 * reasons are in docs/spikes/s1-tenancy.md. If an assertion fails, record what Hibernate actually does there instead
 * of changing the generator.
 */
class HibernateUuidV7Tests {

    @Test
    void hibernatesVersion7StrategyProducesIncreasingVersion7Ids() {
        UUID previous = UuidVersion7Strategy.INSTANCE.generateUuid(null);
        for (int i = 0; i < 10_000; i++) {
            UUID current = UuidVersion7Strategy.INSTANCE.generateUuid(null);
            assertThat(current.version()).isEqualTo(7);
            assertThat(UuidOrder.UNSIGNED.compare(current, previous)).isPositive();
            previous = current;
        }
    }

    @Test
    void theVersion7StyleIsStillIncubating() throws NoSuchFieldException {
        assertThat(UuidGenerator.Style.class.getField("VERSION_7").isAnnotationPresent(Incubating.class))
                .isTrue();
    }
}
