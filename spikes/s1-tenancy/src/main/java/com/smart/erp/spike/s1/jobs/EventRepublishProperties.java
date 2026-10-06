package com.smart.erp.spike.s1.jobs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code min-age} keeps in-flight publications out of a resubmission. */
@ConfigurationProperties(prefix = "spike.events.republish")
public record EventRepublishProperties(
        @DefaultValue("1m") Duration interval,
        @DefaultValue("5m") Duration minAge) {}
