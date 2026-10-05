package com.smart.erp.spike.s1.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class SpikeTestConfiguration {

    @Bean
    static DynamicPropertyRegistrar spikeTestProperties() {
        return registry -> SpikeTestProperties.all().forEach((name, value) -> registry.add(name, () -> value));
    }
}
