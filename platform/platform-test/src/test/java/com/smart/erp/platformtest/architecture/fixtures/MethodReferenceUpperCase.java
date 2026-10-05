package com.smart.erp.platformtest.architecture.fixtures;

import java.util.List;

/** Violates K10 on purpose: a locale-less method reference. */
public class MethodReferenceUpperCase {

    @SuppressWarnings("StringCaseLocaleUsage") // fixture: the ArchUnit rule must catch this
    List<String> upper(List<String> values) {
        return values.stream().map(String::toUpperCase).toList();
    }
}
