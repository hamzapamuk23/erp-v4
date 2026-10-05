package com.smart.erp.platformtest.architecture.fixtures;

/** Violates K10 on purpose. */
public class LocaleLessCaseConversion {

    @SuppressWarnings("StringCaseLocaleUsage") // fixture: the ArchUnit rule must catch this
    String upper(String value) {
        return value.toUpperCase();
    }
}
