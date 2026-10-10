package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.kernel.TenantKey;
import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeDatabases;
import com.smart.erp.spike.s2.support.SpikeTest;
import com.smart.erp.spike.s2.tenancy.TenantStatus;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/**
 * A session belongs to (user, tenant), and on another tenant's host it is not a session at all (design decision 3):
 * 401, the session is not deleted and no tenant context is established for it.
 */
@SpikeTest
class SessionTenantBindingTests {

    private static final String SESSION = "__Host-SESSION";
    private static final String ACME = "acme.erp.test";
    private static final String GLOBEX = "globex.erp.test";

    @LocalServerPort
    int port;

    @Autowired
    FindByIndexNameSessionRepository<?> sessions;

    @Test
    void sessionCookieReplayedOnAnotherTenantsHostIs401() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "ayse");
        browser.putCookie(GLOBEX, SESSION, browser.cookie(ACME, SESSION).orElseThrow());

        assertThat(whoAmI(browser, GLOBEX).status()).isEqualTo(401);
        assertThat(whoAmI(browser, ACME).status()).isEqualTo(200); // refused there, but not deleted
    }

    @Test
    void oneUserGetsOneIndependentSessionPerTenant() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login(ACME, "mm");
        browser.login(GLOBEX, "mm"); // Keycloak SSO answers without a form

        String acmeName = (String) SpikeBrowser.json(whoAmI(browser, ACME)).get("name");
        String globexName = (String) SpikeBrowser.json(whoAmI(browser, GLOBEX)).get("name");
        assertThat(acmeName).startsWith("acme:");
        String subject = acmeName.substring("acme:".length());
        assertThat(globexName).isEqualTo("globex:" + subject);

        String acmeCookie = browser.cookie(ACME, SESSION).orElseThrow();
        assertThat(browser.cookie(GLOBEX, SESSION)).get().isNotEqualTo(acmeCookie);

        // The platform DB is shared by every test and mm logs in to acme elsewhere too: no session counts, only this
        // browser's sessions.
        Map<String, ? extends Session> acmeSessions = sessions.findByPrincipalName(acmeName);
        assertThat(acmeSessions).containsKey(browser.sessionId(ACME).orElseThrow());
        assertThat(acmeSessions).doesNotContainKey(browser.sessionId(GLOBEX).orElseThrow());

        browser.putCookie(GLOBEX, SESSION, acmeCookie);
        assertThat(whoAmI(browser, GLOBEX).status()).isEqualTo(401);
    }

    /**
     * The code and the state of a login started on acme are replayed on globex. Spring Security 7.1.1 compares only the
     * state, not the redirect_uri, so nothing but the ID token check stops this (design decision 7).
     */
    @Test
    void callbackReplayedOnAnotherHostDoesNotLogIn() {
        SpikeBrowser browser = new SpikeBrowser(port);
        SpikeBrowser.Page start =
                browser.get("https://" + ACME + "/oauth2/authorization/keycloak", "Accept", "text/html");
        SpikeBrowser.Page beforeCallback = browser.follow(start, "mm", "https://" + ACME + "/login/oauth2/code/");
        URI callback = URI.create(beforeCallback.location().orElseThrow());
        browser.putCookie(GLOBEX, SESSION, browser.cookie(ACME, SESSION).orElseThrow());

        SpikeBrowser.Page replay = browser.get(
                "https://" + GLOBEX + callback.getRawPath() + "?" + callback.getRawQuery(), "Accept", "text/html");

        assertThat(replay.status()).isEqualTo(403);
        assertThat(replay.body()).isEqualTo("tenant_mismatch");
        assertThat(whoAmI(browser, GLOBEX).status()).isEqualTo(401);
    }

    @Test
    void suspendedTenantRefusesSessionsAndLogins() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.login("initech.erp.test", "ipek");
        assertThat(whoAmI(browser, "initech.erp.test").status()).isEqualTo(200);

        SpikeDatabases.setStatus(new TenantKey("initech"), TenantStatus.SUSPENDED);
        try {
            assertThat(whoAmI(browser, "initech.erp.test").status()).isEqualTo(503);
            SpikeBrowser.Page login =
                    browser.get("https://initech.erp.test/oauth2/authorization/keycloak", "Accept", "text/html");
            assertThat(login.status()).isEqualTo(503);
            assertThat(login.location()).isEmpty();
        } finally {
            SpikeDatabases.setStatus(new TenantKey("initech"), TenantStatus.ACTIVE);
        }
    }

    private static SpikeBrowser.Page whoAmI(SpikeBrowser browser, String host) {
        return browser.get("https://" + host + "/api/whoami", "Accept", "application/json");
    }
}
