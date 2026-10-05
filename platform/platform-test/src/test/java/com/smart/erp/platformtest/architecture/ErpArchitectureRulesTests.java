package com.smart.erp.platformtest.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.platformtest.architecture.fixtures.ComposedScheduledClass;
import com.smart.erp.platformtest.architecture.fixtures.ComposedScheduledJob;
import com.smart.erp.platformtest.architecture.fixtures.EnablesScheduling;
import com.smart.erp.platformtest.architecture.fixtures.LocaleLessCaseConversion;
import com.smart.erp.platformtest.architecture.fixtures.LocaleRootCaseConversion;
import com.smart.erp.platformtest.architecture.fixtures.MethodReferenceLowerCase;
import com.smart.erp.platformtest.architecture.fixtures.MethodReferenceUpperCase;
import com.smart.erp.platformtest.architecture.fixtures.ScheduledJob;
import com.smart.erp.platformtest.architecture.fixtures.SchedulesJob;
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
    void k12RejectsSchedulesContainer() {
        assertThat(violates(ErpArchitectureRules.NO_SPRING_SCHEDULING, SchedulesJob.class))
                .isTrue();
    }

    @Test
    void k12RejectsComposedAnnotationOnMethod() {
        assertThat(violates(ErpArchitectureRules.NO_SPRING_SCHEDULING, ComposedScheduledJob.class))
                .isTrue();
    }

    @Test
    void k12RejectsComposedAnnotationOnClass() {
        assertThat(violates(ErpArchitectureRules.NO_SPRING_SCHEDULING, ComposedScheduledClass.class))
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
    void k10RejectsUpperCaseMethodReference() {
        assertThat(violates(ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION, MethodReferenceUpperCase.class))
                .isTrue();
    }

    @Test
    void k10RejectsLowerCaseMethodReference() {
        assertThat(violates(ErpArchitectureRules.NO_LOCALE_LESS_CASE_CONVERSION, MethodReferenceLowerCase.class))
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
