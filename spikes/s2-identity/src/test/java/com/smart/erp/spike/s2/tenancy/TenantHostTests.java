package com.smart.erp.spike.s2.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TenantHostTests {

    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void aPlainAsciiDnsNameIsKept() {
        assertThat(TenantHost.normalize("acme.erp.test")).contains("acme.erp.test");
    }

    @Test
    void hostNamesAreCaseInsensitive() {
        assertThat(TenantHost.normalize("ACME.ERP.TEST")).contains("acme.erp.test");
    }

    @Test
    void lowerCasingIgnoresTheDefaultLocale() {
        // K10: under tr, "INITECH".toLowerCase() is "ınıtech" (dotless ı), a different, non-ASCII name.
        Locale.setDefault(Locale.forLanguageTag("tr"));
        assertThat(TenantHost.normalize("INITECH.erp.test")).contains("initech.erp.test");
    }

    @Test
    void aTrailingDotOfAFullyQualifiedNameIsDropped() {
        assertThat(TenantHost.normalize("acme.erp.test.")).contains("acme.erp.test");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "İnitech.erp.test", // İnitech: lower-cases to i + combining dot, not ASCII
                "ａcme.erp.test", // fullwidth a
                "acme_tr.erp.test", // '_' is no DNS label character, though a TenantKey may contain it
                "-acme.erp.test",
                "acme-.erp.test",
                "a..b",
                ".acme.erp.test",
                "acme.erp.test..",
                ".",
                "[::1]",
                "::1",
                "acme.erp.test:8443",
                "acme erp.test"
            })
    void anythingElseResolvesToNoHost(String serverName) {
        assertThat(TenantHost.normalize(serverName)).isEmpty();
    }

    @Test
    void anIpLiteralIsNotSingledOut() {
        // Valid labels. It resolves to no tenant because the directory has no row for it (404), not because
        // normalization rejects IP literals.
        assertThat(TenantHost.normalize("127.0.0.1")).contains("127.0.0.1");
    }

    @Test
    void aLabelOf64CharactersIsRejectedAndOf63IsAccepted() {
        assertThat(TenantHost.normalize("a".repeat(64) + ".erp.test")).isEmpty();
        assertThat(TenantHost.normalize("a".repeat(63) + ".erp.test")).isPresent();
    }

    @Test
    void aNameLongerThanTheDnsLimitIsRejected() {
        String label = "a".repeat(60);
        String tooLong = String.join(".", label, label, label, label, label); // 304 characters
        assertThat(TenantHost.normalize(tooLong)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nothingResolvesToNoHost(String serverName) {
        assertThat(TenantHost.normalize(serverName)).isEqualTo(Optional.empty());
    }
}
