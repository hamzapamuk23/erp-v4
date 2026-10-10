package com.smart.erp.spike.s2.tenancy;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Normalizes the request's server name before it is looked up in tenant_domain (doc §4.4 rule 1). Only plain ASCII
 * DNS names pass; anything else resolves to no tenant (404), never to a default one.
 */
public final class TenantHost {

    private static final Pattern LABEL = Pattern.compile("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?");

    private TenantHost() {}

    public static Optional<String> normalize(@Nullable String serverName) {
        if (serverName == null || serverName.isEmpty() || serverName.length() > 254) {
            return Optional.empty();
        }
        String host = serverName.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        for (String label : host.split("\\.", -1)) {
            if (!LABEL.matcher(label).matches()) {
                return Optional.empty();
            }
        }
        return Optional.of(host);
    }
}
