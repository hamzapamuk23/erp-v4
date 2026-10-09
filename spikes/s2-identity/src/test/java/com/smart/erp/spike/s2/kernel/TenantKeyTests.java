package com.smart.erp.spike.s2.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TenantKeyTests {

    @ParameterizedTest
    @ValueSource(strings = {"acme", "globex_2", "a1", "abcdefghijklmnopqrstuvwxyz0123"})
    void acceptsLowerCaseAsciiKeys(String value) {
        assertThat(new TenantKey(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "a",
                "Acme",
                "ACME",
                "1acme",
                "_acme",
                "acme-co",
                "acme co",
                "acme;drop",
                "ışık",
                "abcdefghijklmnopqrstuvwxyz01234"
            })
    void rejectsEverythingElseBeforeAnyLookup(String value) {
        assertThatThrownBy(() -> new TenantKey(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tenant key");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new TenantKey(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void printsAsItsValue() {
        assertThat(new TenantKey("acme")).hasToString("acme");
    }

    @Test
    void isSerializable() throws IOException, ClassNotFoundException {
        // The session principal carries the key, and the session is stored serialized in the platform DB.
        TenantKey original = new TenantKey("acme");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertThat(in.readObject()).isEqualTo(original);
        }
    }
}
