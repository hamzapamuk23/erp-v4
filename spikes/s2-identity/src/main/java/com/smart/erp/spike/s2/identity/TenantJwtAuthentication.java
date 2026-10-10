package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.tenancy.TenantRecord;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The bearer principal: a verified access token bound to the tenant its (iss, organization) names (doc §4.4 rule 2).
 * Named by the token's {@code sub}. It never leaves the request: the bearer chain is stateless, so this authentication
 * is never serialized into a session (its TenantRecord is not Serializable; an attempt would fail, not lose the tenant).
 */
public final class TenantJwtAuthentication extends JwtAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final TenantRecord tenant;

    TenantJwtAuthentication(Jwt jwt, TenantRecord tenant) {
        super(jwt, List.of(), jwt.getSubject());
        this.tenant = tenant;
    }

    public TenantRecord tenant() {
        return tenant;
    }
}
