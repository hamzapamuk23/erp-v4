package com.smart.erp.platformtest.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.platformtest.architecture.fixtures.EnablesScheduling;
import com.smart.erp.platformtest.architecture.fixtures.LocaleLessCaseConversion;
import com.smart.erp.platformtest.architecture.fixtures.LocaleRootCaseConversion;
import com.smart.erp.platformtest.architecture.fixtures.ScheduledJob;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ErpArchitectureRulesTests {

    @Test
    void k12RejectsScheduledMethods() {
        assertThat(violates(ErpArchitectureRules.NO_SPRING_SCHEDULING, ScheduledJob.class))
                .isTrue();
    }

    @Test
    void k12RejectsEnableScheduling() {
        assertThat(violates(ErpArchitectureRules.NO_SPRING_SCHEDULING, EnablesScheduling.class))
                .isTrue();
    }

    @Test
    void k12AcceptsPlainClasses() {
        assertThat(violates(ErpArchitectureRules.NO_SPRING_SCHEDULING, LocaleRootCaseConversion.class))
                .isFalse();
    }

    @Test
    void k10RejectsLocaleLessCaseConversion() {
        assertThat(violates(ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION, LocaleLessCaseConversion.class))
                .isTrue();
    }

    @Test
    void k10AcceptsLocaleRootCaseConversion() {
        assertThat(violates(ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION, LocaleRootCaseConversion.class))
                .isFalse();
    }

    private static boolean violates(ArchRule rule, Class<?>... types) {
        JavaClasses classes = new ClassFileImporter().importClasses(types);
        return rule.evaluate(classes).hasViolation();
    }
}
