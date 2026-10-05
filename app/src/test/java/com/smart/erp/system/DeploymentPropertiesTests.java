package com.smart.erp.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DeploymentPropertiesTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @ParameterizedTest
    @CsvSource({"saas,SAAS", "SaaS,SAAS", "onprem,ONPREM", "on-prem,ONPREM"})
    void bindsKnownModesLeniently(String raw, DeploymentMode expected) {
        runner.withPropertyValues("erp.deployment.mode=" + raw)
                .run(context -> assertThat(
                                context.getBean(DeploymentProperties.class).mode())
                        .isEqualTo(expected));
    }

    @Test
    void rejectsUnknownMode() {
        runner.withPropertyValues("erp.deployment.mode=cloud")
                .run(context -> assertThat(context).getFailure().hasStackTraceContaining("erp.deployment.mode"));
    }

    @Test
    void rejectsEmptyMode() {
        runner.withPropertyValues("erp.deployment.mode=")
                .run(context -> assertThat(context).getFailure().hasStackTraceContaining("erp.deployment.mode"));
    }

    @Test
    void rejectsBlankMode() {
        runner.withPropertyValues("erp.deployment.mode=   ")
                .run(context -> assertThat(context).getFailure().hasStackTraceContaining("erp.deployment.mode"));
    }

    @Test
    void rejectsMissingMode() {
        runner.run(context -> assertThat(context).getFailure().hasMessageContaining("erp.deployment"));
    }

    @Test
    void exposesLowercaseKeys() {
        assertThat(DeploymentMode.SAAS.key()).isEqualTo("saas");
        assertThat(DeploymentMode.ONPREM.key()).isEqualTo("onprem");
    }

    @EnableConfigurationProperties(DeploymentProperties.class)
    static class Config {}
}
