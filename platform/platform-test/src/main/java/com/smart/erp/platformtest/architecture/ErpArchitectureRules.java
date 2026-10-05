package com.smart.erp.platformtest.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;

/**
 * Executable red lines from docs/architecture/v4-platform.md §3.2. Every module applies these to its
 * production classes; each rule is proven by a negative fixture in this module's tests.
 */
public final class ErpArchitectureRules {

    private static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";
    private static final String SCHEDULES = "org.springframework.scheduling.annotation.Schedules";
    private static final String ENABLE_SCHEDULING = "org.springframework.scheduling.annotation.EnableScheduling";

    /** K12: scheduled work runs on the cluster-safe scheduler with an explicit tenant context. */
    public static final ArchRule NO_SPRING_SCHEDULING = CompositeArchRule.of(noMethods()
                    .should()
                    .beAnnotatedWith(SCHEDULED)
                    .orShould()
                    .beAnnotatedWith(SCHEDULES)
                    .allowEmptyShould(true))
            .and(noClasses().should().beAnnotatedWith(ENABLE_SCHEDULING).allowEmptyShould(true))
            .because(
                    "K12: use the cluster-safe scheduler (db-scheduler) with explicit tenant context, never @Scheduled");

    /** K10: case conversion must not depend on the JVM default locale (Turkish dotless i). */
    public static final ArchRule NO_LOCALE_LESS_CASE_CONVERSION = noClasses()
            .should()
            .callMethod(String.class, "toUpperCase")
            .orShould()
            .callMethod(String.class, "toLowerCase")
            .allowEmptyShould(true)
            .because("K10: use toUpperCase(Locale.ROOT)/toLowerCase(Locale.ROOT) or TurkishText.fold()");

    private ErpArchitectureRules() {}
}
