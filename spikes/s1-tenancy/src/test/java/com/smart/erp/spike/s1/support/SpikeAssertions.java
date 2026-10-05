package com.smart.erp.spike.s1.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.core.NestedExceptionUtils;

public final class SpikeAssertions {

    private SpikeAssertions() {}

    /**
     * Asserts on the innermost cause of the failure, or on the failure itself when nothing wraps it. Spring wraps a
     * refused connection in {@code DataSourceUtils.getConnection} but not in {@code doGetConnection}, which jOOQ's
     * transaction-aware proxy uses; the test must not depend on which path a framework takes.
     */
    public static AbstractThrowableAssert<?, ? extends Throwable> assertRootCause(ThrowingCallable call) {
        Throwable failure = catchThrowable(call);
        assertThat(failure).as("expected the call to fail").isNotNull();
        return assertThat(NestedExceptionUtils.getMostSpecificCause(failure));
    }
}
