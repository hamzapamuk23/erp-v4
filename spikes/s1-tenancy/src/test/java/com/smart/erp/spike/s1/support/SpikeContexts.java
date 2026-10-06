package com.smart.erp.spike.s1.support;

import com.smart.erp.spike.s1.SpikeApplication;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the spike outside Spring's test-context cache, so a test observes start-up and shutdown from the first bean to
 * the last. Everything is passed as command-line arguments: they override application.yaml.
 */
public final class SpikeContexts {

    private SpikeContexts() {}

    public static ConfigurableApplicationContext start(String... extraArguments) {
        List<String> arguments = new ArrayList<>();
        SpikeTestProperties.all().forEach((name, value) -> arguments.add("--" + name + "=" + value));
        arguments.addAll(List.of(extraArguments));
        return new SpringApplicationBuilder(SpikeApplication.class)
                .web(WebApplicationType.NONE)
                .run(arguments.toArray(String[]::new));
    }
}
