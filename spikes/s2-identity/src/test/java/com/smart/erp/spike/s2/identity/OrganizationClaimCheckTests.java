package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeTest;
import java.net.URI;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Doc §4.4 rule 1 at login (design decision 7): the hint is the user's to change, the signed ID token is not. Whatever
 * the authorization request asked for, the token must name exactly the host tenant's organization.
 */
@SpikeTest
class OrganizationClaimCheckTests {

    @LocalServerPort
    int port;

    @Test
    void memberOfAnotherOrganizationCannotLogInHere() {
        SpikeBrowser browser = new SpikeBrowser(port);
        SpikeBrowser.Page result = browser.login("acme.erp.test", "zeynep");

        // Keycloak may refuse a non-member itself (an error page) or issue a token without acme (Task 2 learning test).
        // Either way no acme session exists; if the callback reached the application, the ID token check refused it.
        if (result.url().getHost().equals("acme.erp.test")) {
            assertRejectedByTheApplication(result);
        }
        assertNoSession(browser);
    }

    @Test
    void hintRewrittenToAnotherOrganizationIsRejected() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.rewriteAuthorizationRequests(
                scope(scopes -> scopes.replace("organization:acme", "organization:globex")));
        assertRejected(browser, "mm");
    }

    @Test
    void wildcardHintIsRejected() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.rewriteAuthorizationRequests(scope(scopes -> scopes.replace("organization:acme", "organization:*")));
        assertRejected(browser, "mm");
    }

    @Test
    void droppedHintIsRejected() {
        SpikeBrowser browser = new SpikeBrowser(port);
        browser.rewriteAuthorizationRequests(scope(scopes ->
                scopes.replace("organization:acme", "").replaceAll(" +", " ").strip()));
        assertRejected(browser, "mm");
    }

    /** The tampered hint names mm's own organizations (or none), so Keycloak issues a token: the app must refuse it. */
    private static void assertRejected(SpikeBrowser browser, String username) {
        assertRejectedByTheApplication(browser.login("acme.erp.test", username));
        assertNoSession(browser);
    }

    private static void assertRejectedByTheApplication(SpikeBrowser.Page result) {
        assertThat(result.status()).isEqualTo(403);
        assertThat(result.body()).isEqualTo("tenant_mismatch");
    }

    private static void assertNoSession(SpikeBrowser browser) {
        assertThat(browser.get("https://acme.erp.test/api/whoami", "Accept", "application/json")
                        .status())
                .isEqualTo(401);
    }

    /** Rewrites the decoded scope parameter of Keycloak's authorization URL, as a user could in the address bar. */
    private static UnaryOperator<URI> scope(UnaryOperator<String> change) {
        return uri -> {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri).replaceQuery(null);
            SpikeBrowser.query(uri.toString())
                    .forEach((name, values) -> values.forEach(
                            value -> builder.queryParam(name, "scope".equals(name) ? change.apply(value) : value)));
            return builder.encode().build().toUri();
        };
    }
}
