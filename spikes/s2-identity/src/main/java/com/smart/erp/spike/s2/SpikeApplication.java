package com.smart.erp.spike.s2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * S2 identity spike (doc §15.3): BFF login with Keycloak Organizations, tenant by subdomain, sessions in the platform
 * DB, CSRF and a bearer chain. Throwaway code; findings live in docs/spikes/s2-identity.md.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpikeApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpikeApplication.class, args);
    }
}
