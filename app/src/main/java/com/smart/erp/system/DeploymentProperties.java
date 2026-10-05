package com.smart.erp.system;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code erp.deployment.mode=saas|onprem} (doc §4.1). */
@Validated
@ConfigurationProperties(prefix = "erp.deployment")
public record DeploymentProperties(@NotNull DeploymentMode mode) {}
