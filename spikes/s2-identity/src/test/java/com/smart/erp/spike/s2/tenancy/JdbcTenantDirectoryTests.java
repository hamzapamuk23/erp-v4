package com.smart.erp.spike.s2.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.erp.spike.s2.kernel.TenantKey;
import com.smart.erp.spike.s2.support.SpikeDatabases;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcTenantDirectoryTests {

    private static final TenantKey ALPHA = new TenantKey("t1_alpha");
    private static final TenantKey BETA = new TenantKey("t1_beta");
    private static final String ISSUER_A = "https://issuer-a.test/realms/erp";
    private static final String ISSUER_B = "https://issuer-b.test/realms/erp";
    private static final TenantRecord ALPHA_RECORD = new TenantRecord(ALPHA, TenantStatus.ACTIVE, ISSUER_A, "alpha");
    private static final TenantRecord BETA_RECORD = new TenantRecord(BETA, TenantStatus.ACTIVE, ISSUER_B, "beta");

    private final JdbcTenantDirectory directory = new JdbcTenantDirectory(SpikeDatabases.platformDataSource());

    @BeforeEach
    void registerTheTenants() {
        SpikeDatabases.register(ALPHA_RECORD, "alpha.t1.test", "www.alpha.t1.test");
        SpikeDatabases.register(BETA_RECORD, "beta.t1.test");
    }

    @Test
    void findsTenantByHost() {
        assertThat(directory.findByHost("alpha.t1.test")).contains(ALPHA_RECORD);
        assertThat(directory.findByHost("www.alpha.t1.test")).contains(ALPHA_RECORD);
        assertThat(directory.findByHost("beta.t1.test")).contains(BETA_RECORD);
    }

    @Test
    void unknownHostFindsNothing() {
        assertThat(directory.findByHost("unknown.t1.test")).isEmpty();
    }

    @Test
    void findsTenantByIssuerAndAlias() {
        assertThat(directory.findByIdentity(ISSUER_A, "alpha")).contains(ALPHA_RECORD);
        assertThat(directory.findByIdentity(ISSUER_B, "beta")).contains(BETA_RECORD);
    }

    @Test
    void sameAliasUnderAnotherIssuerDoesNotResolve() {
        // ADR-0005 separate-realm exception: (issuer, alias) is the key, the alias alone is not.
        assertThat(directory.findByIdentity(ISSUER_B, "alpha")).isEmpty();
        assertThat(directory.findByIdentity(ISSUER_A, "beta")).isEmpty();
    }

    @Test
    void requireReturnsAKnownTenant() {
        assertThat(directory.require(ALPHA)).isEqualTo(ALPHA_RECORD);
    }

    @Test
    void requireFailsForUnknownTenant() {
        assertThatThrownBy(() -> directory.require(new TenantKey("t1_nobody")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("t1_nobody");
    }

    @Test
    void statusIsReadOnEveryLookup() {
        assertThat(directory.findByHost("alpha.t1.test"))
                .map(TenantRecord::status)
                .contains(TenantStatus.ACTIVE);

        SpikeDatabases.setStatus(ALPHA, TenantStatus.SUSPENDED);

        assertThat(directory.findByHost("alpha.t1.test"))
                .map(TenantRecord::status)
                .contains(TenantStatus.SUSPENDED);
        assertThat(directory.findByIdentity(ISSUER_A, "alpha"))
                .map(TenantRecord::status)
                .contains(TenantStatus.SUSPENDED);
        assertThat(directory.require(ALPHA).status()).isEqualTo(TenantStatus.SUSPENDED);
    }
}
