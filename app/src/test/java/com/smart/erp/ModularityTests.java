package com.smart.erp;

import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    // Shared test support is on the test classpath but is not an application module.
    private static final ApplicationModules MODULES = ApplicationModules.of(
            ErpApplication.class, JavaClass.Predicates.resideInAPackage("com.smart.erp.platformtest.."));

    @Test
    void moduleStructureIsValid() {
        MODULES.verify();
    }
}
