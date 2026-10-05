package com.smart.erp.platformtest.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.Set;

/**
 * Executable red lines from docs/architecture/v4-platform.md §3.2. Every module applies these to its
 * production classes; each rule is proven by a negative fixture in this module's tests.
 */
public final class ErpArchitectureRules {

    private static final String SCHEDULED = "org.springframework.scheduling.annotation.Scheduled";
    private static final String SCHEDULES = "org.springframework.scheduling.annotation.Schedules";
    private static final String ENABLE_SCHEDULING = "org.springframework.scheduling.annotation.EnableScheduling";

    /**
     * K12: scheduled work runs on the cluster-safe scheduler with an explicit tenant context. Composed
     * annotations are caught too (meta-annotation check).
     */
    public static final ArchRule NO_SPRING_SCHEDULING = CompositeArchRule.of(noMethods()
                    .should()
                    .beMetaAnnotatedWith(SCHEDULED)
                    .orShould()
                    .beMetaAnnotatedWith(SCHEDULES)
                    .allowEmptyShould(true))
            .and(noClasses()
                    .should()
                    .beMetaAnnotatedWith(SCHEDULED)
                    .orShould()
                    .beMetaAnnotatedWith(SCHEDULES)
                    .orShould()
                    .beMetaAnnotatedWith(ENABLE_SCHEDULING)
                    .allowEmptyShould(true))
            .because(
                    "K12: use the cluster-safe scheduler (db-scheduler) with explicit tenant context, never @Scheduled");

    private static final Set<String> LOCALE_DEPENDENT_CASE_METHODS = Set.of("toUpperCase", "toLowerCase");

    private static final DescribedPredicate<JavaAccess<?>> LOCALE_LESS_CASE_CONVERSION =
            new DescribedPredicate<>("the no-arg String.toUpperCase()/toLowerCase(), called or referenced") {
                @Override
                public boolean test(JavaAccess<?> access) {
                    return access.getTarget() instanceof CodeUnitAccessTarget target
                            && target.getOwner().isEquivalentTo(String.class)
                            && LOCALE_DEPENDENT_CASE_METHODS.contains(target.getName())
                            && target.getRawParameterTypes().isEmpty();
                }
            };

    /**
     * K10: case conversion must not depend on the JVM default locale (Turkish dotless i). Matches
     * calls and method references ({@code String::toUpperCase}).
     */
    public static final ArchRule NO_LOCALE_LESS_CASE_CONVERSION = noClasses()
            .should()
            .accessTargetWhere(LOCALE_LESS_CASE_CONVERSION)
            .allowEmptyShould(true)
            .because("K10: use toUpperCase(Locale.ROOT)/toLowerCase(Locale.ROOT) or TurkishText.fold()");

    private ErpArchitectureRules() {}
}
