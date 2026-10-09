package com.smart.erp.spike.s2.support;

import com.smart.erp.spike.s2.SpikeApplication;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the spike outside Spring's test-context cache, in the same environment as {@link SpikeTest}, so a test observes
 * start-up and shutdown from the first bean to the last. The port is random; read it from {@code local.server.port}.
 */
public final class SpikeContexts {

    private SpikeContexts() {}

    /** {@code arguments} are command-line arguments: they override the spike environment and application.yaml. */
    public static ConfigurableApplicationContext start(String... arguments) {
        List<String> all = new ArrayList<>();
        all.add("--server.port=0");
        all.addAll(List.of(arguments));
        return new SpringApplicationBuilder(SpikeApplication.class)
                .initializers(new SpikeEnvironment())
                .run(all.toArray(String[]::new));
    }
}
