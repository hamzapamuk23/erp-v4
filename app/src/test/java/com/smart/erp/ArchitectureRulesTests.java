package com.smart.erp;

import com.smart.erp.platformtest.architecture.ErpArchitectureRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTests {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.smart.erp");

    @Test
    void k10NoLocaleLessCaseConversion() {
        ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION.check(PRODUCTION_CLASSES);
    }

    @Test
    void k12NoSpringScheduling() {
        ErpArchitectureRules.NO_SPRING_SCHEDULING.check(PRODUCTION_CLASSES);
    }
}
