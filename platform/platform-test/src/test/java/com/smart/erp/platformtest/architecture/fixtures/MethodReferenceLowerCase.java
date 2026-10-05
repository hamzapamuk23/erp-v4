package com.smart.erp.platformtest.architecture.fixtures;

import java.util.function.Function;

/** Violates K10 on purpose: a locale-less method reference held in a variable. */
public class MethodReferenceLowerCase {

    @SuppressWarnings("StringCaseLocaleUsage") // fixture: the ArchUnit rule must catch this
    Function<String, String> lower() {
        Function<String, String> f = String::toLowerCase;
        return f;
    }
}
