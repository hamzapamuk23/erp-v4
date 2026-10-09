package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class LazyClientRegistrationRepositoryTests {

    private static final String ISSUER = "https://idp.test/realms/erp";
    private static final IdentityProperties PROPERTIES =
            new IdentityProperties(URI.create(ISSUER), "erp-web", "web-client-secret");

    private final AtomicInteger discoveries = new AtomicInteger();

    @Test
    void unknownRegistrationIdIsNullWithoutDiscovery() {
        LazyClientRegistrationRepository repository = new LazyClientRegistrationRepository(PROPERTIES, this::discover);

        assertThat(repository.findByRegistrationId("github")).isNull();
        assertThat(discoveries).hasValue(0);
    }

    @Test
    void discoveryRunsOnceAndIsCached() {
        LazyClientRegistrationRepository repository = new LazyClientRegistrationRepository(PROPERTIES, this::discover);

        ClientRegistration first = repository.findByRegistrationId(LazyClientRegistrationRepository.REGISTRATION_ID);
        ClientRegistration second = repository.findByRegistrationId(LazyClientRegistrationRepository.REGISTRATION_ID);

        assertThat(second).isSameAs(first);
        assertThat(discoveries).hasValue(1);
        assertThat(first.getRegistrationId()).isEqualTo(LazyClientRegistrationRepository.REGISTRATION_ID);
        assertThat(first.getClientId()).isEqualTo("erp-web");
        assertThat(first.getScopes()).containsExactly("openid", "profile", "email");
        assertThat(first.getRedirectUri()).isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
    }

    @Test
    void failedDiscoveryIsRetriedOnTheNextLogin() {
        LazyClientRegistrationRepository repository = new LazyClientRegistrationRepository(PROPERTIES, issuer -> {
            if (discoveries.get() == 0) {
                discoveries.incrementAndGet();
                throw new IllegalArgumentException("Unable to resolve the Configuration with the provided Issuer");
            }
            return discover(issuer);
        });

        assertThatThrownBy(() -> repository.findByRegistrationId(LazyClientRegistrationRepository.REGISTRATION_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findByRegistrationId(LazyClientRegistrationRepository.REGISTRATION_ID))
                .isNotNull();
        assertThat(discoveries).hasValue(2);
    }

    @Test
    void registrationRequiresPkce() {
        LazyClientRegistrationRepository repository = new LazyClientRegistrationRepository(PROPERTIES, this::discover);

        assertThat(repository
                        .findByRegistrationId(LazyClientRegistrationRepository.REGISTRATION_ID)
                        .getClientSettings()
                        .isRequireProofKey())
                .isTrue();
    }

    /** Like ClientRegistrations.fromIssuerLocation: named after the issuer's host, endpoints from the metadata. */
    private ClientRegistration.Builder discover(String issuer) {
        discoveries.incrementAndGet();
        return ClientRegistration.withRegistrationId(URI.create(issuer).getHost())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri(issuer + "/protocol/openid-connect/auth")
                .tokenUri(issuer + "/protocol/openid-connect/token")
                .jwkSetUri(issuer + "/protocol/openid-connect/certs")
                .issuerUri(issuer);
    }
}
