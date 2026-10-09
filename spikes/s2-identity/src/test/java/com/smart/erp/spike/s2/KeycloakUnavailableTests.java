package com.smart.erp.spike.s2;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.erp.spike.s2.support.SpikeBrowser;
import com.smart.erp.spike.s2.support.SpikeContexts;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Design decision 8: the client registration is discovered at the first login, not at start-up. On-prem compose may
 * start the application before Keycloak; it must come up and be ready, and only a login fails while Keycloak is away.
 */
class KeycloakUnavailableTests {

    @Test
    void startsAndIsReadyWithoutKeycloak() {
        // Port 9 (discard) on loopback: nothing listens, the connection is refused at once. The redirect filter logs
        // every refused discovery at ERROR with its stack trace; here that is the expected outcome.
        try (ConfigurableApplicationContext context = SpikeContexts.start(
                "--erp.identity.issuer-uri=http://127.0.0.1:9/realms/erp",
                "--logging.level.org.springframework.security.oauth2.client.web"
                        + ".OAuth2AuthorizationRequestRedirectFilter=off")) {
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            SpikeBrowser browser = new SpikeBrowser(port);
            String readiness = "http://127.0.0.1:" + port + "/actuator/health/readiness";

            assertThat(browser.get(readiness).status()).isEqualTo(200);
            assertThat(browser.get("https://acme.erp.test/oauth2/authorization/keycloak", "Accept", "text/html")
                            .status())
                    .isBetween(500, 599);
            assertThat(browser.get(readiness).status()).isEqualTo(200);
        }
    }
}
