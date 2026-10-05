package com.smart.erp.spike.s1;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * S1 tenancy spike (doc §15.3): routing DataSource, Hibernate, jOOQ, Modulith events and db-scheduler on several tenant
 * databases. Throwaway code; findings live in docs/spikes/s1-tenancy.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpikeApplication.class, args);
    }

    /** Server time is the source of truth (doc §3.1.4, §6.1); tests may replace it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
