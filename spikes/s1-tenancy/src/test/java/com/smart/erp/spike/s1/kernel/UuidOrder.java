package com.smart.erp.spike.s1.kernel;

import java.util.Comparator;
import java.util.UUID;

/** PostgreSQL orders {@code uuid} values as unsigned bytes; {@link UUID#compareTo} compares signed longs. */
final class UuidOrder {

    static final Comparator<UUID> UNSIGNED = Comparator.comparing(UUID::getMostSignificantBits, Long::compareUnsigned)
            .thenComparing(UUID::getLeastSignificantBits, Long::compareUnsigned);

    private UuidOrder() {}
}
