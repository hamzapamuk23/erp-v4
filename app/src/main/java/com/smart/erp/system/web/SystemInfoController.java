package com.smart.erp.system.web;

import com.smart.erp.system.DeploymentProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Walking-skeleton endpoint: proves SPA → API wiring. Superseded by /api/v1/bootstrap in Phase 3 (doc §8.1). */
@RestController
@RequestMapping("/api/v1/system")
class SystemInfoController {

    static final String DEV_VERSION = "0.0.0-dev";

    private final String version;
    private final DeploymentProperties deployment;

    SystemInfoController(ObjectProvider<BuildProperties> buildProperties, DeploymentProperties deployment) {
        BuildProperties build = buildProperties.getIfAvailable();
        this.version = build != null && build.getVersion() != null ? build.getVersion() : DEV_VERSION;
        this.deployment = deployment;
    }

    @GetMapping("/info")
    SystemInfoResponse info() {
        return new SystemInfoResponse(version, deployment.mode().key());
    }
}
