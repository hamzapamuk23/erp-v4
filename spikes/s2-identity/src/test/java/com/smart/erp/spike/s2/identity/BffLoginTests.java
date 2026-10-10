package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeDatabases;
import com.smart.erp.spike.s2.support.SpikeTest;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.util.MultiValueMap;

@SpikeTest
class BffLoginTests {

    private static final String SESSION = "__Host-SESSION";

    @LocalServerPort
    int port;

    @Test
    void authorizationRequestCarriesTheOrganizationHintPkceAndTheTenantsRedirectUri() {
        SpikeBrowser.Page redirect = new SpikeBrowser(port)
                .get("https://acme.erp.test/oauth2/authorization/keycloak", "Accept", "text/html");

        assertThat(redirect.status()).isEqualTo(302);
        MultiValueMap<String, String> query =
                SpikeBrowser.query(redirect.location().orElseThrow());
        assertThat(query.getFirst("scope").split(" ")).contains("openid", "organization:acme");
        assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("https://acme.erp.test/login/oauth2/code/keycloak");
    }

    @Test
    void loginBindsANewSessionToTenantAndUserInThePlatformDatabase() {
        SpikeBrowser browser = new SpikeBrowser(port);
        SpikeBrowser.Page start =
                browser.get("https://acme.erp.test/oauth2/authorization/keycloak", "Accept", "text/html");
        String beforeLogin = browser.cookie("acme.erp.test", SESSION).orElseThrow();

        assertThat(browser.follow(start, "ayse", null).body()).isEqualTo("ok");

        assertThat(browser.cookie("acme.erp.test", SESSION)).get().isNotEqualTo(beforeLogin);
        Map<String, Object> me =
                SpikeBrowser.json(browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json"));
        assertThat(me).containsEntry("tenant", "acme").containsEntry("authentication", "OAuth2AuthenticationToken");
        assertThat((String) me.get("name")).startsWith("acme:");
        assertThat(SpikeDatabases.platformDatabase()
                        .sql("select principal_name from spring_session")
                        .query(String.class)
                        .list())
                .contains((String) me.get("name"));
    }

    @Test
    void sessionCookieIsHostOnlySecureHttpOnlyAndLax() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login("acme.erp.test", "ayse");

        assertThat(browser.setCookieHeaders("acme.erp.test"))
                .filteredOn(header -> header.startsWith(SESSION + "="))
                .isNotEmpty()
                .allSatisfy(header -> assertThat(header)
                        .contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax")
                        .doesNotContainIgnoringCase("Domain="));
    }

    /** The browser gets no token at all; the session keeps only the ID token (RP-initiated logout), never access/refresh. */
    @Test
    void browserIsNeverGivenATokenAndTheSessionHoldsOnlyTheIdToken() throws ParseException {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login("acme.erp.test", "ayse");
        browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json");

        Pattern jwt = Pattern.compile("eyJ[\\w-]+\\.eyJ[\\w-]+\\.[\\w-]+");
        assertThat(browser.appResponses()).allSatisfy(page -> {
            assertThat(page.body()).doesNotContainPattern(jwt);
            assertThat(page.headers().map().toString()).doesNotContainPattern(jwt);
        });
        assertThat(browser.appResponses().stream()
                        .flatMap(page -> page.headers().allValues("Set-Cookie").stream())
                        .map(header -> header.substring(0, header.indexOf('='))))
                .containsOnly(SESSION);

        // This browser's session only: the platform DB is shared by every test.
        List<Map<String, Object>> attributes = SpikeDatabases.platformDatabase()
                .sql("""
                        select a.attribute_name, a.attribute_bytes from spring_session_attributes a
                        join spring_session s on s.primary_id = a.session_primary_id where s.session_id = ?""")
                .param(browser.sessionId("acme.erp.test").orElseThrow())
                .query()
                .listOfRows();
        assertThat(attributes)
                .extracting(row -> (String) row.get("attribute_name"))
                .noneMatch(name -> name.contains("AuthorizedClient"));
        List<String> typesInSession = new ArrayList<>();
        for (Map<String, Object> row : attributes) {
            Matcher token = jwt.matcher(new String((byte[]) row.get("attribute_bytes"), StandardCharsets.ISO_8859_1));
            while (token.find()) {
                typesInSession.add(
                        SignedJWT.parse(token.group()).getJWTClaimsSet().getStringClaim("typ"));
            }
        }
        assertThat(typesInSession).isNotEmpty().containsOnly("ID"); // Keycloak: "ID", "Bearer", "Refresh"
    }

    /**
     * Pins design decision 1: from a trusted (internal) address, X-Forwarded-Host overrides Host, and the tenant follows
     * it. Caddy sends both with the same value; production narrows internal-proxies to Caddy (Phase 1/7).
     */
    @Test
    void forwardedHostFromATrustedProxyDecidesTheTenant() {
        SpikeBrowser.Page redirect = new SpikeBrowser(port)
                .get(
                        "https://acme.erp.test/oauth2/authorization/keycloak",
                        "Accept",
                        "text/html",
                        "X-Forwarded-Host",
                        "globex.erp.test");

        assertThat(redirect.status()).isEqualTo(302);
        MultiValueMap<String, String> query =
                SpikeBrowser.query(redirect.location().orElseThrow());
        assertThat(query.getFirst("scope").split(" ")).contains("organization:globex");
        assertThat(query.getFirst("redirect_uri")).isEqualTo("https://globex.erp.test/login/oauth2/code/keycloak");
    }

    @Test
    void unknownHostIs404AndStartsNoLogin() {
        SpikeBrowser.Page page = new SpikeBrowser(port)
                .get("https://unknown.erp.test/oauth2/authorization/keycloak", "Accept", "text/html");

        assertThat(page.status()).isEqualTo(404);
        assertThat(page.location()).isEmpty();
    }

    @Test
    void withoutASessionTheApiAnswers401AndPagesRedirectToLogin() {
        SpikeBrowser browser = new SpikeBrowser(port);

        assertThat(browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json")
                        .status())
                .isEqualTo(401);
        assertThat(browser.get("https://acme.erp.test/", "Accept", "text/html").location())
                .hasValueSatisfying(location -> assertThat(location).endsWith("/oauth2/authorization/keycloak"));
    }
}
