package com.smart.erp.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.ErpIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@ErpIntegrationTest
class SystemInfoControllerTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ObjectProvider<BuildProperties> buildProperties;

    @Test
    void returnsVersionAndDeploymentMode() {
        String expectedVersion = Optional.ofNullable(buildProperties.getIfAvailable())
                .map(BuildProperties::getVersion)
                .orElse(SystemInfoController.DEV_VERSION);

        assertThat(mvc.get().uri("/api/v1/system/info"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"version": "%s", "deploymentMode": "onprem"}
                        """.formatted(expectedVersion));
    }
}
