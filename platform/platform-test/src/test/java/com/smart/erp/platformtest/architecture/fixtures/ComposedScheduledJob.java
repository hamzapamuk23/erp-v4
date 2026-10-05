package com.smart.erp.platformtest.architecture.fixtures;

/** Violates K12 on purpose: the method uses a composed annotation meta-annotated with @Scheduled. */
public class ComposedScheduledJob {

    @ComposedScheduled
    void run() {}
}
