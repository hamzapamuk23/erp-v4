package com.smart.erp.platformtest.architecture.fixtures;

import java.util.Locale;

/** Complies with K10 and K12. */
public class LocaleRootCaseConversion {

    String upper(String value) {
        return value.toUpperCase(Locale.ROOT);
    }
}
