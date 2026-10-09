package com.smart.erp.spike.s2.identity;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code erp.identity.*}: the identity provider's issuer (ADR-0005) and the BFF's confidential client (ADR-0006). The
 * issuer and the secret come from the installer's configuration, never from the repository (K9).
 */
@ConfigurationProperties(prefix = "erp.identity")
public record IdentityProperties(URI issuerUri, String webClientId, String webClientSecret) {

    public IdentityProperties {
        Objects.requireNonNull(issuerUri, "erp.identity.issuer-uri");
        Objects.requireNonNull(webClientId, "erp.identity.web-client-id");
        Objects.requireNonNull(webClientSecret, "erp.identity.web-client-secret");
    }
}
