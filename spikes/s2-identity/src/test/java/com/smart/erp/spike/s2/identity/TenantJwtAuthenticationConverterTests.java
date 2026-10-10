package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s2.kernel.TenantKey;
import com.smart.erp.spike.s2.tenancy.TenantDirectory;
import com.smart.erp.spike.s2.tenancy.TenantRecord;
import com.smart.erp.spike.s2.tenancy.TenantStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * The tenant of a bearer token is the one registered for its (issuer, organization) (doc §4.4 rule 2, design decision
 * 2). Unit level, against an in-memory directory: the HTTP behaviour is {@link BearerChainTests}.
 */
class TenantJwtAuthenticationConverterTests {

    private static final String ISSUER = "https://sso.erp.test/realms/erp";
    private static final TenantRecord ACME =
            new TenantRecord(new TenantKey("acme"), TenantStatus.ACTIVE, ISSUER, "acme");

    private final TenantJwtAuthenticationConverter converter = new TenantJwtAuthenticationConverter(directory(ACME));

    @Test
    void listClaimIsResolved() {
        TenantJwtAuthentication authentication = convert(ISSUER, List.of("acme"));

        assertThat(authentication.tenant()).isEqualTo(ACME);
        assertThat(authentication.getName()).isEqualTo("service-account-acme");
    }

    @Test
    void mapClaimIsResolved() {
        TenantJwtAuthentication authentication = convert(ISSUER, Map.of("acme", Map.of("id", "6b5c")));

        assertThat(authentication.tenant()).isEqualTo(ACME);
    }

    @Test
    void tokenWithoutTheClaimIsRefused() {
        assertThatThrownBy(() -> convert(ISSUER, null)).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void tokenWithTwoOrganizationsIsRefused() {
        TenantJwtAuthenticationConverter both = new TenantJwtAuthenticationConverter(
                directory(ACME, new TenantRecord(new TenantKey("globex"), TenantStatus.ACTIVE, ISSUER, "globex")));

        assertThatThrownBy(() -> both.convert(jwt(ISSUER, List.of("acme", "globex"))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void unregisteredOrganizationIsRefused() {
        assertThatThrownBy(() -> convert(ISSUER, List.of("hooli"))).isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void sameAliasUnderAnotherIssuerIsNotResolved() {
        assertThatThrownBy(() -> convert("https://other.erp.test/realms/erp", List.of("acme")))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    private TenantJwtAuthentication convert(String issuer, Object organization) {
        return (TenantJwtAuthentication) converter.convert(jwt(issuer, organization));
    }

    private static Jwt jwt(String issuer, Object organization) {
        Jwt.Builder jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("service-account-acme")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (organization != null) {
            jwt.claim(KeycloakOrganizations.CLAIM, organization);
        }
        return jwt.build();
    }

    private static TenantDirectory directory(TenantRecord... records) {
        return new TenantDirectory() {
            @Override
            public Optional<TenantRecord> findByHost(String normalizedHost) {
                return Optional.empty();
            }

            @Override
            public Optional<TenantRecord> findByIdentity(String issuer, String organizationAlias) {
                return List.of(records).stream()
                        .filter(tenant -> tenant.issuer().equals(issuer)
                                && tenant.organizationAlias().equals(organizationAlias))
                        .findFirst();
            }

            @Override
            public TenantRecord require(TenantKey key) {
                return List.of(records).stream()
                        .filter(tenant -> tenant.key().equals(key))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No such tenant: " + key));
            }
        };
    }
}
