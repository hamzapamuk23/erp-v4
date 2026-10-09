package com.smart.erp.spike.s2.identity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The only Keycloak-specific knowledge in application code (ADR-0005): the organization scope hint and the shape of the
 * organization claim. Keycloak 26 emits ["acme"] by default and {"acme": {...}} when the organization's id or
 * attributes are mapped; both name the same organizations.
 */
public final class KeycloakOrganizations {

    public static final String CLAIM = "organization";

    private KeycloakOrganizations() {}

    public static String scopeFor(String alias) {
        return "organization:" + alias;
    }

    /** The organization aliases a claim names; a malformed claim names none. */
    public static Set<String> aliases(@Nullable Object claim) {
        Collection<?> values = switch (claim) {
            case null -> List.of();
            case String alias -> List.of(alias);
            case Collection<?> list -> list;
            case Map<?, ?> map -> map.keySet();
            default -> List.of(claim);
        };
        Set<String> aliases = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String alias) || alias.isBlank()) {
                return Set.of();
            }
            aliases.add(alias);
        }
        return Collections.unmodifiableSet(aliases);
    }
}
