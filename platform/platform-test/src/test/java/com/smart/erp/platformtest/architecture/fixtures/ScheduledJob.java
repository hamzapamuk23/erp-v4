package com.smart.erp.platformtest.architecture.fixtures;

import org.springframework.scheduling.annotation.Scheduled;

/** Violates K12 on purpose. */
public class ScheduledJob {

    @Scheduled(fixedDelay = 1000)
    void run() {}
}
