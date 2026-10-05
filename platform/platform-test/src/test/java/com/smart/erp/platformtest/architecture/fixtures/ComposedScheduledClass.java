package com.smart.erp.platformtest.architecture.fixtures;

/** Violates K12 on purpose: the class itself carries a composed @Scheduled annotation. */
@ComposedScheduled
public class ComposedScheduledClass {

    void noop() {}
}
