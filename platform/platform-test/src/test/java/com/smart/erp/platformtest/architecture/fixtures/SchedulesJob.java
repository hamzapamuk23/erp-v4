package com.smart.erp.platformtest.architecture.fixtures;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

/** Violates K12 on purpose: repeatable @Scheduled collected by @Schedules. */
public class SchedulesJob {

    @Schedules({@Scheduled(fixedDelay = 1000), @Scheduled(fixedRate = 2000)})
    void run() {}
}
