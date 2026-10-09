package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.kernel.TenantKey;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * The browser principal: an OIDC user bound to one tenant (doc §4.4: a session belongs to (user, tenant)). Its name
 * "<tenant>:<sub>" is what Spring Session indexes, so the sessions of (user, tenant) can be found without touching the
 * user's other tenants.
 */
public final class TenantOidcUser extends DefaultOidcUser {

    private static final long serialVersionUID = 1L;

    private final TenantKey tenant;

    TenantOidcUser(TenantKey tenant, OidcUser user) {
        super(user.getAuthorities(), user.getIdToken(), user.getUserInfo(), IdTokenClaimNames.SUB);
        this.tenant = tenant;
    }

    public TenantKey tenant() {
        return tenant;
    }

    @Override
    public String getName() {
        return tenant.value() + ":" + getSubject();
    }
}
