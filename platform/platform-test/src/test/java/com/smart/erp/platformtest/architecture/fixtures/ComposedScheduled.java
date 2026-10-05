package com.smart.erp.platformtest.architecture.fixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.scheduling.annotation.Scheduled;

/** A composed annotation that hides {@code @Scheduled} behind a custom name. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Scheduled(fixedDelay = 1000)
public @interface ComposedScheduled {}
