package com.smart.erp.spike.s1;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    @Test
    void moduleStructureIsValid() {
        ApplicationModules.of(SpikeApplication.class).verify();
    }
}
