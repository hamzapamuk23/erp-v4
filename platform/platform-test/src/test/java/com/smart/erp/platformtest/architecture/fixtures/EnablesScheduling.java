package com.smart.erp.platformtest.architecture.fixtures;

import org.springframework.scheduling.annotation.EnableScheduling;

/** Violates K12 on purpose. */
@EnableScheduling
public class EnablesScheduling {

    void noop() {}
}
