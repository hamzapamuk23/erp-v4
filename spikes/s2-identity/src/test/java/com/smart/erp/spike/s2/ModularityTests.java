package com.smart.erp.spike.s2;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(SpikeApplication.class).verify();
    }
}
