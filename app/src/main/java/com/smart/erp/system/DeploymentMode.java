package com.smart.erp.system;

import java.util.Locale;

/** Installation shape (doc §4.1). Only tenant sourcing and ops defaults may depend on it, never business code. */
public enum DeploymentMode {
    SAAS,
    ONPREM;

    /** Stable lowercase key used in configuration and API payloads. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
