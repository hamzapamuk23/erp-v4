package com.smart.erp.spike.s1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

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
}
