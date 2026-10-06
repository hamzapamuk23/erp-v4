package com.smart.erp.spike.s1.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class UuidV7Tests {

    private static final Instant NOON = Instant.parse("2026-10-05T12:00:00.123Z");

    @Test
    void producesVersion7WithTheRfcVariant() {
        UUID id = UuidV7.next();
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsTheClocksMillisecond() {
        UuidV7 generator = new UuidV7(Clock.fixed(NOON, ZoneOffset.UTC), new SplittableRandom(1));
        assertThat(generator.generate().getMostSignificantBits() >>> 16).isEqualTo(NOON.toEpochMilli());
    }

    @Test
    void isStrictlyIncreasingWithinOneMillisecondAndAcrossCounterOverflow() {
        // More than 4096 ids in one frozen millisecond: the 12-bit counter overflows several times.
        UuidV7 generator = new UuidV7(Clock.fixed(NOON, ZoneOffset.UTC), new SplittableRandom(2));
        assertStrictlyIncreasing(generator::generate, 10_000);
    }

    @Test
    void staysIncreasingWhenTheClockStepsBack() {
        MutableClock clock = new MutableClock(NOON);
        UuidV7 generator = new UuidV7(clock, new SplittableRandom(3));
        UUID before = generator.generate();
        clock.set(NOON.minusSeconds(1));
        assertThat(UuidOrder.UNSIGNED.compare(generator.generate(), before)).isPositive();
    }

    @Test
    void theSharedGeneratorIsStrictlyIncreasing() {
        assertStrictlyIncreasing(UuidV7::next, 100_000);
    }

    @Test
    void isUniqueAcrossThreads() {
        var ids = ConcurrentHashMap.<UUID>newKeySet();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int thread = 0; thread < 8; thread++) {
                executor.execute(() -> {
                    for (int i = 0; i < 25_000; i++) {
                        ids.add(UuidV7.next());
                    }
                });
            }
        }
        assertThat(ids).hasSize(200_000);
    }

    private static void assertStrictlyIncreasing(Supplier<UUID> ids, int count) {
        UUID previous = ids.get();
        for (int i = 1; i < count; i++) {
            UUID current = ids.get();
            assertThat(UuidOrder.UNSIGNED.compare(current, previous))
                    .as("id #%d %s after %s", i, current, previous)
                    .isPositive();
            previous = current;
        }
    }
}
