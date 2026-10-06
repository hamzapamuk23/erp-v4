package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.task.ExecutionComplete;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import java.time.Duration;
import java.time.Instant;

/**
 * Fixed delay whose first run is one interval after start-up. db-scheduler's FixedDelay runs at once, and walking the
 * tenants at start-up would open a pool per tenant (doc §6.12).
 */
record DelayedFixedDelay(Duration interval) implements Schedule {

    @Override
    public Instant getNextExecutionTime(ExecutionComplete executionComplete) {
        return executionComplete.getTimeDone().plus(interval);
    }

    @Override
    public boolean isDeterministic() {
        return false;
    }
}
