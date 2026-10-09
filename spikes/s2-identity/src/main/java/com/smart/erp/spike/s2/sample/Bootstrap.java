package com.smart.erp.spike.s2.sample;

/**
 * What the SPA needs on start-up (doc §9.7): the tenant and user of the session and the session's CSRF token with the
 * header to send it in. The token is the XOR-masked one, different in every response; it is not an OIDC token.
 */
public record Bootstrap(String tenant, String subject, String name, String csrfHeader, String csrfToken) {}
