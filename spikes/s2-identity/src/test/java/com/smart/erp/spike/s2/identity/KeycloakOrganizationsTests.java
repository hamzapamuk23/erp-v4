package com.smart.erp.spike.s2.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakOrganizationsTests {

    @Test
    void aListNamesItsOrganizations() {
        assertThat(KeycloakOrganizations.aliases(List.of("acme"))).containsExactly("acme");
    }

    @Test
    void aMapNamesItsKeys() {
        assertThat(KeycloakOrganizations.aliases(Map.of("acme", Map.of("id", "0b9a3c5e"))))
                .containsExactly("acme");
    }

    @Test
    void aSingleStringNamesOneOrganization() {
        assertThat(KeycloakOrganizations.aliases("acme")).containsExactly("acme");
    }

    @Test
    void noClaimNamesNone() {
        assertThat(KeycloakOrganizations.aliases(null)).isEmpty();
    }

    @Test
    void aNonStringEntryMakesTheClaimMalformed() {
        assertThat(KeycloakOrganizations.aliases(List.of(1))).isEmpty();
    }

    @Test
    void aBlankEntryMakesTheClaimMalformed() {
        assertThat(KeycloakOrganizations.aliases(List.of(""))).isEmpty();
    }

    @Test
    void theScopeHintNamesTheAlias() {
        assertThat(KeycloakOrganizations.scopeFor("acme")).isEqualTo("organization:acme");
    }
}
