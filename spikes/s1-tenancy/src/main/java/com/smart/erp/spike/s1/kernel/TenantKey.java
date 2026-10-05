package com.smart.erp.spike.s1.kernel;

import java.util.regex.Pattern;

/**
 * Identifies a tenant (doc §4.2). The value ends up in database names ({@code erp_t_<key>}) and log lines, so it is
 * restricted to lower-case ASCII letters, digits and underscore, 2–30 characters, starting with a letter.
 */
public record TenantKey(String value) {

    private static final Pattern FORMAT = Pattern.compile("[a-z][a-z0-9_]{1,29}");

    public TenantKey {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid tenant key: '" + value + "' (expected " + FORMAT.pattern() + ")");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
