package com.smart.erp.spike.s1.jobs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "spike.jobs")
public record JobsProperties(
        @DefaultValue("1s") Duration pollingInterval,
        @DefaultValue("4") int threads,
        @DefaultValue("30s") Duration shutdownMaxWait) {}
