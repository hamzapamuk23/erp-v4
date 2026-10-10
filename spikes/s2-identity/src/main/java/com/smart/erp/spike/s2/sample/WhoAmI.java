package com.smart.erp.spike.s2.sample;

/** The caller as the application sees it; {@code authentication} is the authentication's simple class name. */
public record WhoAmI(String tenant, String name, String authentication) {}
