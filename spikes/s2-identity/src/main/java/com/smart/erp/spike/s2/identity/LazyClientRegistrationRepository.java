package com.smart.erp.spike.s2.identity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

/**
 * The BFF's single client registration, discovered at the first login instead of at start-up (design decision 8).
 * Boot's issuer-uri registration fetches the OIDC metadata while the context starts, so an application that on-prem
 * compose starts before Keycloak would not come up. A failed discovery is not cached: the next login tries again.
 */
final class LazyClientRegistrationRepository implements ClientRegistrationRepository {

    static final String REGISTRATION_ID = "keycloak";

    /** The only list of the scopes the BFF asks for; the organization hint is added per request. */
    private static final List<String> SCOPES = List.of(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL);

    private final IdentityProperties properties;
    private final Function<String, ClientRegistration.Builder> discovery;
    private final Object lock = new Object();
    private volatile @Nullable ClientRegistration registration;

    LazyClientRegistrationRepository(IdentityProperties properties) {
        this(properties, ClientRegistrations::fromIssuerLocation);
    }

    LazyClientRegistrationRepository(
            IdentityProperties properties, Function<String, ClientRegistration.Builder> discovery) {
        this.properties = properties;
        this.discovery = discovery;
    }

    /** The registration's scopes plus {@code scope}, in a stable order. */
    static Set<String> scopesWith(String scope) {
        Set<String> scopes = new LinkedHashSet<>(SCOPES);
        scopes.add(scope);
        return Collections.unmodifiableSet(scopes);
    }

    @Override
    public @Nullable ClientRegistration findByRegistrationId(String registrationId) {
        if (!REGISTRATION_ID.equals(registrationId)) {
            return null;
        }
        ClientRegistration current = registration;
        if (current == null) {
            synchronized (lock) {
                current = registration;
                if (current == null) {
                    current = discover();
                    registration = current;
                }
            }
        }
        return current;
    }

    private ClientRegistration discover() {
        return discovery
                .apply(properties.issuerUri().toString())
                // Discovery names the registration after the issuer's host; {registrationId} must expand to ours.
                .registrationId(REGISTRATION_ID)
                .clientId(properties.webClientId())
                .clientSecret(properties.webClientSecret())
                .scope(SCOPES)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                // Spring Security 7.1.1 adds PKCE to a confidential client only when asked (design decision 7).
                .clientSettings(ClientRegistration.ClientSettings.builder()
                        .requireProofKey(true)
                        .build())
                .build();
    }
}
