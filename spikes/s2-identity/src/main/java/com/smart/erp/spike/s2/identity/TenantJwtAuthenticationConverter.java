package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.tenancy.TenantDirectory;
import com.smart.erp.spike.s2.tenancy.TenantRecord;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Resolves the tenant from the token's (iss, organization) (doc §4.4 rule 2). Integration clients get the claim from a
 * hardcoded mapper; a token that names no organization or several is not an API token (ADR-0040).
 */
final class TenantJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final TenantDirectory tenants;

    TenantJwtAuthenticationConverter(TenantDirectory tenants) {
        this.tenants = tenants;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> organizations =
                KeycloakOrganizations.aliases(jwt.getClaims().get(KeycloakOrganizations.CLAIM));
        if (organizations.size() != 1) {
            throw new InvalidBearerTokenException("The token must name exactly one organization");
        }
        TenantRecord tenant = tenants.findByIdentity(
                        String.valueOf(jwt.getIssuer()),
                        organizations.iterator().next())
                .orElseThrow(() -> new InvalidBearerTokenException("The token's organization is not a tenant"));
        return new TenantJwtAuthentication(jwt, tenant);
    }
}
