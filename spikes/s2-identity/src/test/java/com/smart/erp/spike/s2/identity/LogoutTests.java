package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeDatabases;
import com.smart.erp.spike.s2.support.SpikeKeycloak;
import com.smart.erp.spike.s2.support.SpikeTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.util.MultiValueMap;

/**
 * Logout ends the application session and Keycloak's SSO session through RP-initiated logout (ID token hint from the
 * principal, post-logout redirect to the tenant's own host). Like every state-changing request it needs the CSRF token:
 * a sibling subdomain must not be able to log a user out.
 */
@SpikeTest
class LogoutTests {

    private static final String SESSION = "__Host-SESSION";
    private static final String ACME = "acme.erp.test";
    private static final String GLOBEX = "globex.erp.test";

    @LocalServerPort
    int port;

    @Test
    void logoutWithoutTheTokenIs403AndKeepsTheSession() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");

        SpikeBrowser.Page logout = browser.post("https://" + ACME + "/logout", Map.of(), "Accept", "text/html");

        assertThat(logout.status()).isEqualTo(403);
        assertThat(whoAmI(browser, ACME).status()).isEqualTo(200);
    }

    @Test
    void logoutEndsTheSessionAndHandsOverToKeycloak() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");
        String sessionId = browser.sessionId(ACME).orElseThrow();
        String oldCookie = browser.cookie(ACME, SESSION).orElseThrow();

        SpikeBrowser.Page logout = logout(browser, ACME);

        assertThat(logout.status()).isEqualTo(302);
        String location = logout.location().orElseThrow();
        assertThat(location).startsWith(SpikeKeycloak.issuer() + "/protocol/openid-connect/logout?");
        MultiValueMap<String, String> query = SpikeBrowser.query(location);
        assertThat(query.getFirst("id_token_hint")).isNotBlank();
        assertThat(query.getFirst("post_logout_redirect_uri")).isEqualTo("https://acme.erp.test/");
        // The platform DB is shared by every test class: only this browser's session is looked at.
        assertThat(SpikeDatabases.platformDatabase()
                        .sql("select count(*) from spring_session where session_id = ?")
                        .param(sessionId)
                        .query(Long.class)
                        .single())
                .isZero();
        browser.putCookie(ACME, SESSION, oldCookie);
        assertThat(whoAmI(browser, ACME).status()).isEqualTo(401);

        // Keycloak ends its SSO session and sends the browser back to the tenant.
        SpikeBrowser.Page keycloak = browser.get(location, "Accept", "text/html");
        assertThat(keycloak.status()).isEqualTo(302);
        assertThat(keycloak.location()).get().asString().startsWith("https://acme.erp.test/");
    }

    /**
     * Pinned finding: logout is per tenant session. Following the logout through Keycloak ends the user's SSO session
     * (a new login now shows the form), yet the application session on the other tenant lives on until its own logout
     * or timeout; back-channel logout is a Phase 3 decision.
     */
    @Test
    void logoutOnOneTenantLeavesTheOtherTenantsSessionAlone() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "mm");
        browser.login(GLOBEX, "mm"); // Keycloak SSO answers without a form

        SpikeBrowser.Page logout = logout(browser, ACME);
        assertThat(logout.status()).isEqualTo(302);
        SpikeBrowser.Page keycloak = browser.get(logout.location().orElseThrow(), "Accept", "text/html");
        assertThat(keycloak.status()).isEqualTo(302);
        assertThat(keycloak.location()).get().asString().startsWith("https://acme.erp.test/");

        // The SSO session is over: a new authorization request now meets the login form instead of a formless answer.
        SpikeBrowser.Page start =
                browser.get("https://" + ACME + "/oauth2/authorization/keycloak", "Accept", "text/html");
        SpikeBrowser.Page login = browser.get(start.location().orElseThrow(), "Accept", "text/html");
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body()).contains("id=\"kc-form-login\"");

        // ... and still the other tenant's application session answers.
        assertThat(whoAmI(browser, GLOBEX).status()).isEqualTo(200);
    }

    private static SpikeBrowser.Page logout(SpikeBrowser browser, String host) {
        SpikeBrowser.Page bootstrap = browser.get("https://" + host + "/bootstrap", "Accept", "application/json");
        assertThat(bootstrap.status()).isEqualTo(200);
        String token = (String) SpikeBrowser.json(bootstrap).get("csrfToken");
        return browser.post("https://" + host + "/logout", Map.of(), "Accept", "text/html", "X-CSRF-TOKEN", token);
    }

    private static SpikeBrowser.Page whoAmI(SpikeBrowser browser, String host) {
        return browser.get("https://" + host + "/api/whoami", "Accept", "application/json");
    }
}
