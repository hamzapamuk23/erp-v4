package com.smart.erp.spike.s2.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.smart.erp.spike.s2.identity.KeycloakOrganizations;
import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeKeycloak;
import java.text.ParseException;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins what Keycloak 26.8 does with Organizations before the application relies on it (doc §4.4, ADR-0005). Talks to
 * Keycloak only. A failure here is a finding: record the actual behaviour in docs/spikes/s2-identity.md and revisit the
 * tasks that assume it; do not bend the assertion to make it pass.
 */
class KeycloakOrganizationsLearningTests {

    @Test
    void scopeHintPutsOnlyTheRequestedOrganizationIntoBothTokens() throws ParseException {
        SpikeKeycloak.Tokens tokens = SpikeKeycloak.codeFlow("mm", "openid organization:globex");

        assertThat(organizations(tokens.idToken())).containsExactly("globex");
        assertThat(organizations(tokens.accessToken())).containsExactly("globex");
    }

    @Test
    void nonMemberAskingForAnOrganizationGetsNoClaimForIt() throws ParseException {
        SpikeKeycloak.Tokens tokens = SpikeKeycloak.codeFlow("zeynep", "openid organization:acme");

        assertThat(organizations(tokens.idToken())).doesNotContain("acme");
    }

    @Test
    void wildcardHintPutsEveryMembershipIntoTheClaim() throws ParseException {
        SpikeKeycloak.Tokens tokens = SpikeKeycloak.codeFlow("mm", "openid organization:*");

        assertThat(organizations(tokens.idToken())).containsExactlyInAnyOrder("acme", "globex");
    }

    @Test
    void hardcodedMapperGivesAServiceAccountItsTenantAndTheApiAudience() throws ParseException {
        JWTClaimsSet claims = SignedJWT.parse(SpikeKeycloak.clientCredentials("acme-integration"))
                .getJWTClaimsSet();

        assertThat(KeycloakOrganizations.aliases(claims.getClaim(KeycloakOrganizations.CLAIM)))
                .containsExactly("acme");
        assertThat(claims.getAudience()).contains("erp-api");
    }

    /** Provisioning must register every tenant host on erp-web (Phase 2): '*' matches only at the end of a path. */
    @Test
    void subdomainWildcardRedirectUriDoesNotCoverANewTenant() {
        assertThat(SpikeKeycloak.createClient("""
                        {"clientId": "wildcard-probe", "publicClient": true, "redirectUris": ["https://*.erp.test/*"]}""")).isEqualTo(201);

        SpikeBrowser.Page page = new SpikeBrowser(0)
                .get(SpikeKeycloak.authorizationUrl("wildcard-probe", "https://newco.erp.test/callback", "openid"));

        assertThat(page.status()).isEqualTo(400);
        assertThat(page.body()).contains("redirect_uri");
    }

    /** TenantKey allows '_' (S1); a DNS label does not (TenantHostTests). The organization alias must take the key. */
    @Test
    void organizationAliasAcceptsTheTenantKeyFormat() {
        assertThat(SpikeKeycloak.createOrganization("acme_tr", "acme-tr.example"))
                .isEqualTo(201);
    }

    private static Set<String> organizations(String jwt) throws ParseException {
        return KeycloakOrganizations.aliases(
                SignedJWT.parse(jwt).getJWTClaimsSet().getClaim(KeycloakOrganizations.CLAIM));
    }
}
