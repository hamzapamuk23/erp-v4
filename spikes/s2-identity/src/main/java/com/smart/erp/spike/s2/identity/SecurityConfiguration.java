package com.smart.erp.spike.s2.identity;

import com.smart.erp.spike.s2.kernel.TenantContext;
import com.smart.erp.spike.s2.tenancy.TenantDirectory;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfiguration {

    /** Health stays reachable on the internal address without a tenant host (readiness = platform DB). */
    @Bean
    @Order(0)
    SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/actuator/health/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** Path 1 of ADR-0040: the browser session (BFF). The tenant is the host's; login and session must agree with it. */
    @Bean
    @Order(2)
    SecurityFilterChain browserChain(
            HttpSecurity http, TenantDirectory tenants, OAuth2AuthorizationRequestResolver authorizationRequests)
            throws Exception {
        RequestMatcher apiCalls = new OrRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                PathPatternRequestMatcher.withDefaults().matcher("/bootstrap"));
        return http.addFilterBefore(new BrowserTenantFilter(tenants), CsrfFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2Login(login -> login
                        // The authorization endpoint is the login page: no generated page, and no eager discovery to
                        // list registrations (design decision 8).
                        .loginPage("/oauth2/authorization/" + LazyClientRegistrationRepository.REGISTRATION_ID)
                        .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequests))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(new TenantOidcUserService(tenants)))
                        // A redirect would restart login, Keycloak's SSO session would answer at once: a loop.
                        .failureHandler(SecurityConfiguration::loginFailed))
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), apiCalls))
                .build();
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(IdentityProperties properties) {
        return new LazyClientRegistrationRepository(properties);
    }

    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new DiscardingAuthorizedClientRepository();
    }

    /**
     * Adds the host tenant's organization hint (doc §4.4 rule 1). The hint is not a security boundary. PKCE comes from
     * the registration (requireProofKey, design decision 7).
     */
    @Bean
    OAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository registrations, TenantDirectory tenants) {
        DefaultOAuth2AuthorizationRequestResolver resolver = new DefaultOAuth2AuthorizationRequestResolver(
                registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(
                request -> request.scopes(LazyClientRegistrationRepository.scopesWith(KeycloakOrganizations.scopeFor(
                        tenants.require(TenantContext.require()).organizationAlias()))));
        return resolver;
    }

    private static void loginFailed(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter()
                .write(
                        exception instanceof OAuth2AuthenticationException oauth
                                ? oauth.getError().getErrorCode()
                                : "login_failed");
    }
}
