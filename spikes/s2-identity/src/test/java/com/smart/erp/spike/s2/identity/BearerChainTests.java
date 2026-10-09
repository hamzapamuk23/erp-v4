package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.smart.erp.spike.s2.kernel.TenantKey;
import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeDatabases;
import com.smart.erp.spike.s2.support.SpikeKeycloak;
import com.smart.erp.spike.s2.support.SpikeTest;
import com.smart.erp.spike.s2.tenancy.TenantStatus;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Path 2 of ADR-0040: the stateless OIDC bearer chain. The tenant is the token's (iss, organization), never a session's;
 * a token that is valid but wrong (no organization, two, an unregistered one, another audience, a foreign key, a
 * suspended tenant, another tenant's host) is refused, and a bad token never falls back to the session cookie
 * (design decisions 2 and 3, review focus 5).
 */
@SpikeTest
class BearerChainTests {

    private static final String API = "https://api.erp.test/api/whoami";
    private static final String ACME = "acme.erp.test";
    private static final String GLOBEX = "globex.erp.test";
    private static final String SESSION = "__Host-SESSION";

    @LocalServerPort
    int port;

    @Test
    void integrationClientIsServedInItsTenant() {
        SpikeBrowser browser = new SpikeBrowser(port);
        long sessionsBefore = sessionCount();

        SpikeBrowser.Page page = get(browser, API, SpikeKeycloak.clientCredentials("acme-integration"));

        assertThat(page.status()).isEqualTo(200);
        Map<String, Object> whoAmI = SpikeBrowser.json(page);
        assertThat(whoAmI).containsEntry("tenant", "acme").containsEntry("authentication", "TenantJwtAuthentication");
        assertThat(page.headers().allValues("Set-Cookie")).as("Set-Cookie").isEmpty();
        assertThat(sessionCount()).as("sessions in the platform DB").isEqualTo(sessionsBefore);
    }

    @Test
    void bearerPostNeedsNoCsrfToken() {
        SpikeBrowser browser = new SpikeBrowser(port);

        SpikeBrowser.Page page = browser.post(
                "https://api.erp.test/api/echo",
                Map.of(),
                "Authorization",
                "Bearer " + SpikeKeycloak.clientCredentials("acme-integration"),
                "Accept",
                "application/json");

        assertThat(page.status()).isEqualTo(200);
        assertThat(SpikeBrowser.json(page)).containsEntry("tenant", "acme");
    }

    @Test
    void tokenWithoutOrganizationIs401() {
        SpikeBrowser.Page page = get(new SpikeBrowser(port), API, SpikeKeycloak.clientCredentials("noorg-integration"));

        assertThat(page.status()).isEqualTo(401);
        assertThat(wwwAuthenticate(page)).contains("invalid_token");
    }

    @Test
    void tokenWithTwoOrganizationsIs401() {
        SpikeBrowser.Page page = get(new SpikeBrowser(port), API, SpikeKeycloak.clientCredentials("multi-integration"));

        assertThat(page.status()).isEqualTo(401);
    }

    @Test
    void tokenForAnUnregisteredOrganizationIs401() {
        SpikeBrowser.Page page =
                get(new SpikeBrowser(port), API, SpikeKeycloak.clientCredentials("unknown-integration"));

        assertThat(page.status()).isEqualTo(401);
    }

    @Test
    void tokenWithoutTheApiAudienceIs401() {
        SpikeBrowser.Page page = get(new SpikeBrowser(port), API, SpikeKeycloak.clientCredentials("noaud-integration"));

        assertThat(page.status()).isEqualTo(401);
    }

    /**
     * The key is not in the realm's JWKS, so the signature stops it. The issuer check is not proven separately: a
     * separate realm is design decision 2's subject.
     */
    @Test
    void tokenSignedWithAForeignKeyIs401() throws Exception {
        RSAKey foreignKey = new RSAKeyGenerator(2048).keyID("foreign").generate();
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("http://evil.test/realms/erp")
                .subject("mallory")
                .audience("erp-api")
                .claim("organization", List.of("acme"))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(foreignKey.getKeyID())
                        .build(),
                claims);
        token.sign(new RSASSASigner(foreignKey));

        SpikeBrowser.Page page = get(new SpikeBrowser(port), API, token.serialize());

        assertThat(page.status()).isEqualTo(401);
        assertThat(wwwAuthenticate(page)).contains("invalid_token");
    }

    @Test
    void invalidBearerTokenDoesNotFallBackToTheSession() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");
        String sessionId = browser.sessionId(ACME).orElseThrow();
        long accessedBefore = lastAccess(sessionId);

        SpikeBrowser.Page page = get(browser, "https://" + ACME + "/api/whoami", "not-a-jwt");

        assertThat(page.status()).isEqualTo(401);
        assertThat(wwwAuthenticate(page)).contains("invalid_token");
        assertThat(lastAccess(sessionId)).as("the session was not read").isEqualTo(accessedBefore);
    }

    @Test
    void tokenOnAnotherTenantsHostIs403() {
        SpikeBrowser browser = new SpikeBrowser(port);
        String token = SpikeKeycloak.clientCredentials("acme-integration");

        assertThat(get(browser, "https://" + GLOBEX + "/api/whoami", token).status())
                .isEqualTo(403);
        SpikeBrowser.Page own = get(browser, "https://" + ACME + "/api/whoami", token);
        assertThat(own.status()).isEqualTo(200);
        assertThat(SpikeBrowser.json(own)).containsEntry("tenant", "acme");
    }

    @Test
    void suspendedTenantsTokenIs503() {
        String token = SpikeKeycloak.clientCredentials("initech-integration");

        SpikeDatabases.setStatus(new TenantKey("initech"), TenantStatus.SUSPENDED);
        try {
            assertThat(get(new SpikeBrowser(port), API, token).status()).isEqualTo(503);
        } finally {
            SpikeDatabases.setStatus(new TenantKey("initech"), TenantStatus.ACTIVE);
        }
    }

    @Test
    void bearerRequestIgnoresAnySessionCookie() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");
        String sessionId = browser.sessionId(ACME).orElseThrow();
        SpikeBrowser.Page asSession = browser.get("https://" + ACME + "/api/whoami", "Accept", "application/json");
        String sessionName = (String) SpikeBrowser.json(asSession).get("name");
        assertThat(sessionName).startsWith("acme:");
        long accessedBefore = lastAccess(sessionId);

        SpikeBrowser.Page page =
                get(browser, "https://" + ACME + "/api/whoami", SpikeKeycloak.clientCredentials("acme-integration"));

        assertThat(page.status()).isEqualTo(200);
        Map<String, Object> whoAmI = SpikeBrowser.json(page);
        assertThat(whoAmI).containsEntry("tenant", "acme").containsEntry("authentication", "TenantJwtAuthentication");
        assertThat(whoAmI.get("name")).isNotEqualTo(sessionName);
        assertThat(page.headers().allValues("Set-Cookie")).as("Set-Cookie").isEmpty();
        assertThat(browser.cookie(ACME, SESSION))
                .as("session cookie in the jar")
                .isPresent();
        assertThat(lastAccess(sessionId)).as("the session was not read").isEqualTo(accessedBefore);

        // Control: the same cookie without a bearer header does read the session, so the check above can fail.
        browser.get("https://" + ACME + "/api/whoami", "Accept", "application/json");
        assertThat(lastAccess(sessionId))
                .as("the session is read by a cookie-only request")
                .isGreaterThan(accessedBefore);
    }

    @Test
    void bootstrapIsNotABearerEndpoint() {
        SpikeBrowser.Page page = get(
                new SpikeBrowser(port),
                "https://api.erp.test/bootstrap",
                SpikeKeycloak.clientCredentials("acme-integration"));

        assertThat(page.status()).isEqualTo(403);
    }

    private static SpikeBrowser.Page get(SpikeBrowser browser, String url, String token) {
        return browser.get(url, "Authorization", "Bearer " + token, "Accept", "application/json");
    }

    private static String wwwAuthenticate(SpikeBrowser.Page page) {
        return String.join(" ", page.headers().allValues("WWW-Authenticate"));
    }

    private static long sessionCount() {
        return SpikeDatabases.platformDatabase()
                .sql("select count(*) from spring_session")
                .query(Long.class)
                .single();
    }

    private static long lastAccess(String sessionId) {
        return SpikeDatabases.platformDatabase()
                .sql("select last_access_time from spring_session where session_id = ?")
                .param(sessionId)
                .query(Long.class)
                .single();
    }
}
