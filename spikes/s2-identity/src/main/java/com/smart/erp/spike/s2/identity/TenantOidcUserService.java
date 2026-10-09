package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.kernel.TenantContext;
import com.smart.erp.spike.s2.tenancy.TenantDirectory;
import com.smart.erp.spike.s2.tenancy.TenantRecord;
import java.util.Set;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Loads the OIDC user at the login callback and binds it to the host's tenant, which {@link BrowserTenantFilter} has
 * bound for the request. The signed ID token must name exactly that tenant (doc §4.4 rule 1, design decision 7).
 */
final class TenantOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final TenantDirectory tenants;

    TenantOidcUserService(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = delegate.loadUser(request);
        TenantRecord tenant = tenants.require(TenantContext.require());
        OidcIdToken idToken = user.getIdToken();
        boolean sameIssuer = tenant.issuer().equals(String.valueOf(idToken.getIssuer()));
        Set<String> organizations =
                KeycloakOrganizations.aliases(idToken.getClaims().get(KeycloakOrganizations.CLAIM));
        if (!sameIssuer || !organizations.equals(Set.of(tenant.organizationAlias()))) {
            // The hint is the user's to change; the signed ID token is not (doc §4.4 rule 1, design decision 7).
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "tenant_mismatch", "The ID token does not name exactly this host's organization", null));
        }
        return new TenantOidcUser(tenant.key(), user);
    }
}
