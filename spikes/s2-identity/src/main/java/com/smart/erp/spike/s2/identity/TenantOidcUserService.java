package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.kernel.TenantContext;
import com.smart.erp.spike.s2.tenancy.TenantDirectory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Loads the OIDC user at the login callback and binds it to the host's tenant, which {@link BrowserTenantFilter} has
 * bound for the request.
 */
final class TenantOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();

    @SuppressWarnings("UnusedVariable") // Task 4 checks the ID token's issuer and organization claim against it
    private final TenantDirectory tenants;

    TenantOidcUserService(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        return new TenantOidcUser(TenantContext.require(), delegate.loadUser(request));
    }
}
