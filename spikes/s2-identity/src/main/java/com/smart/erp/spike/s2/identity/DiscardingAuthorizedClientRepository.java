package com.smart.erp.spike.s2.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * The BFF keeps no tokens (design decision 5, ADR-0006). It calls no API downstream with the user's access token, so
 * the access and refresh tokens of a login are dropped here instead of being stored. Boot's default
 * (InMemoryOAuth2AuthorizedClientService) would collect them in instance memory without bound and differ between
 * instances. The session keeps only the ID token, inside the principal, for RP-initiated logout. Consequence: the
 * application session lives independently of Keycloak's; absolute lifetime and revocation are Phase 3 decisions.
 */
final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    @SuppressWarnings("TypeParameterUnusedInFormals") // the interface's signature
    public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Discarded on purpose: see the class comment.
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // Nothing was stored.
    }
}
