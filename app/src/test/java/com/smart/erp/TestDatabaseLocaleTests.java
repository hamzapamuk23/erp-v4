package com.smart.erp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The test database is initialised like deploy/compose (doc §7.8): UTF8, builtin provider, C.UTF-8. */
@ErpIntegrationTest
class TestDatabaseLocaleTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void usesTheBuiltinUtf8Locale() {
        var row = jdbc.sql("""
                        select pg_encoding_to_char(encoding) as encoding,
                               datlocprovider::text as provider,
                               datlocale
                          from pg_database
                         where datname = current_database()
                        """).query().singleRow();
        assertThat(row)
                .containsEntry("encoding", "UTF8")
                .containsEntry("provider", "b")
                .containsEntry("datlocale", "C.UTF-8");
    }
}
