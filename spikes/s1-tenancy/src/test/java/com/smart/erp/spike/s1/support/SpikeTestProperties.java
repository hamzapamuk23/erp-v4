package com.smart.erp.spike.s1.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** Properties every spike test context gets: the fixture's connection settings plus test timings. */
public final class SpikeTestProperties {

    private SpikeTestProperties() {}

    public static Map<String, String> all() {
        Map<String, String> properties = new LinkedHashMap<>(SpikeDatabases.applicationProperties());
        properties.put("erp.tenancy.datasource.connection-timeout", "2s");
        properties.put("spike.jobs.polling-interval", "100ms");
        return properties;
    }
}
