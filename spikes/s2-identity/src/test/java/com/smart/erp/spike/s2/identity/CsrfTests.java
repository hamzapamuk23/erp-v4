package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Tenants are sibling subdomains, i.e. the same site: SameSite=Lax does not separate them (doc §6.3.1). Every
 * state-changing request needs the CSRF token bound to the session (design decision 6): Spring Security's default,
 * pinned here. A token cookie is no defence, because a sibling can plant one for the whole parent domain.
 */
@SpikeTest
class CsrfTests {

    private static final String ACME = "acme.erp.test";
    private static final String GLOBEX = "globex.erp.test";
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    @LocalServerPort
    int port;

    @Test
    void bootstrapGivesTheSpaTenantUserAndAMaskedCsrfToken() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");

        SpikeBrowser.Page first = bootstrap(browser, ACME);
        SpikeBrowser.Page second = bootstrap(browser, ACME);

        assertThat(first.status()).isEqualTo(200);
        Map<String, Object> firstBody = SpikeBrowser.json(first);
        Map<String, Object> secondBody = SpikeBrowser.json(second);
        assertThat(firstBody)
                .containsEntry("tenant", "acme")
                .containsEntry("csrfHeader", CSRF_HEADER)
                .containsKeys("subject", "name");
        assertThat((String) firstBody.get("subject")).isNotBlank();
        assertThat((String) firstBody.get("name")).isNotBlank();
        assertThat((String) firstBody.get("csrfToken")).isNotBlank();
        assertThat((String) secondBody.get("csrfToken")).isNotBlank();
        // The XOR mask makes every response carry a different value for the same session token.
        assertThat(secondBody.get("csrfToken")).isNotEqualTo(firstBody.get("csrfToken"));
        assertThat(echo(browser, ACME, (String) firstBody.get("csrfToken")).status())
                .isEqualTo(200);
        assertThat(echo(browser, ACME, (String) secondBody.get("csrfToken")).status())
                .isEqualTo(200);
    }

    /** The SPA sees the 401 and sends the browser to login (doc §9.7); a redirect would hide it from fetch(). */
    @Test
    void bootstrapWithoutASessionIs401() {
        SpikeBrowser.Page page =
                new SpikeBrowser(port).get("https://" + ACME + "/bootstrap", "Accept", "application/json");

        assertThat(page.status()).isEqualTo(401);
        assertThat(page.location()).isEmpty();
    }

    @Test
    void stateChangingRequestWithoutTheTokenIs403() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");

        assertThat(browser.post("https://" + ACME + "/api/echo", Map.of(), "Accept", "application/json")
                        .status())
                .isEqualTo(403);
    }

    @Test
    void stateChangingRequestWithTheBootstrapTokenPasses() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");

        SpikeBrowser.Page page = echo(browser, ACME, token(bootstrap(browser, ACME)));

        assertThat(page.status()).isEqualTo(200);
        assertThat(SpikeBrowser.json(page)).containsEntry("tenant", "acme");
    }

    /**
     * Why design decision 6 rejects the cookie-based double submit: a sibling subdomain plants XSRF-TOKEN=forged for the
     * parent domain, the forged request repeats it in the header and in the form, and the cookie matches itself. The
     * token lives in the session, so the repeated value matches nothing.
     */
    @Test
    void forgedDoubleSubmitCookieIsUseless() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");
        browser.putCookie(ACME, "XSRF-TOKEN", "forged");

        SpikeBrowser.Page page = browser.post(
                "https://" + ACME + "/api/echo",
                Map.of("_csrf", "forged"),
                "Accept",
                "application/json",
                "X-XSRF-TOKEN",
                "forged");

        assertThat(page.status()).isEqualTo(403);
    }

    /** A token is bound to its session: the same user's globex session cannot vouch for a request on acme. */
    @Test
    void tokenFromTheSameUsersOtherTenantSessionIsRejected() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "mm");
        browser.login(GLOBEX, "mm"); // Keycloak SSO answers without a form
        String globexToken = token(bootstrap(browser, GLOBEX));

        assertThat(echo(browser, ACME, globexToken).status()).isEqualTo(403);
        assertThat(echo(browser, GLOBEX, globexToken).status()).isEqualTo(200); // the token itself is valid
    }

    private static SpikeBrowser.Page bootstrap(SpikeBrowser browser, String host) {
        return browser.get("https://" + host + "/bootstrap", "Accept", "application/json");
    }

    private static String token(SpikeBrowser.Page bootstrap) {
        assertThat(bootstrap.status()).isEqualTo(200);
        return (String) SpikeBrowser.json(bootstrap).get("csrfToken");
    }

    private static SpikeBrowser.Page echo(SpikeBrowser browser, String host, String token) {
        return browser.post(
                "https://" + host + "/api/echo", Map.of(), "Accept", "application/json", CSRF_HEADER, token);
    }
}
