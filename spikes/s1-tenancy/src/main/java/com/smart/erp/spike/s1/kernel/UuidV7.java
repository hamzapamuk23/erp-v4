package com.smart.erp.spike.s1.kernel;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Application-side UUIDv7 (RFC 9562 §5.7, ADR-0013): a 48-bit Unix-millisecond timestamp, a 12-bit counter in
 * {@code rand_a} (§6.2 method 1, seeded randomly in its lower half every millisecond) and 62 random bits. Ids from one
 * generator are strictly increasing in PostgreSQL {@code uuid} order, also when the clock stalls or steps back: the
 * counter keeps counting and, when it overflows, the timestamp moves one millisecond ahead.
 */
public final class UuidV7 {

    private static final UuidV7 SHARED = new UuidV7(Clock.systemUTC(), new SecureRandom());
    private static final int COUNTER_MAX = 0xFFF;
    private static final int COUNTER_SEED_BOUND = 1 << 11;

    private final Clock clock;
    private final RandomGenerator random;
    private long lastMillis = -1;
    private int counter;

    UuidV7(Clock clock, RandomGenerator random) {
        this.clock = clock;
        this.random = random;
    }

    public static UUID next() {
        return SHARED.generate();
    }

    synchronized UUID generate() {
        long now = clock.millis();
        if (now > lastMillis) {
            lastMillis = now;
            counter = random.nextInt(COUNTER_SEED_BOUND);
        } else if (++counter > COUNTER_MAX) {
            lastMillis++;
            counter = random.nextInt(COUNTER_SEED_BOUND);
        }
        long mostSignificant = (lastMillis << 16) | 0x7000L | counter;
        long leastSignificant = (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
