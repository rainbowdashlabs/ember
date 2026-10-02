/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.MovableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HourlyCountersTest {
    private static final Instant HOUR = Instant.parse("2026-03-01T10:00:00Z");

    private final MovableClock clock = new MovableClock(HOUR.plusSeconds(60));
    private final HourlyCounters<String> counters = new HourlyCounters<>("test counters", 2, clock);
    private final List<String> writes = new ArrayList<>();

    private void write(Instant hour, String key, long[] delta) {
        writes.add("%s %s %d %d".formatted(hour, key, delta[0], delta[1]));
    }

    @Test
    void addsUpPerKeyAndHour() {
        counters.add("a", 1, 2);
        counters.add("a", 3, 4);
        counters.add("b", 1, 1);

        var a = counters.snapshot().stream()
                .filter(bucket -> bucket.key().equals("a"))
                .findFirst()
                .orElseThrow();
        assertEquals(HOUR, a.hour());
        assertArrayEquals(new long[] {4, 6}, a.totals());
        assertEquals(2, counters.size());
    }

    @Test
    void writesOnlyTheDeltaSinceTheLastWrite() {
        counters.add("a", 1, 1);
        counters.flush(true, this::write);
        counters.flush(true, this::write);
        counters.add("a", 2, 0);
        counters.flush(true, this::write);

        assertEquals(List.of(HOUR + " a 1 1", HOUR + " a 2 0"), writes);
    }

    @Test
    void currentHourIsLeftAloneUnlessAskedFor() {
        counters.add("a", 1, 1);

        counters.flush(false, this::write);

        assertEquals(List.of(), writes);
        assertEquals(1, counters.size());
    }

    @Test
    void pastHourLeavesMemoryOnceWritten() {
        counters.add("a", 1, 1);
        counters.add("b", 0, 0);
        clock.advance(Duration.ofHours(1));

        counters.flush(false, this::write);

        assertEquals(List.of(HOUR + " a 1 1"), writes);
        assertEquals(0, counters.size(), "the empty past bucket goes too");
    }

    @Test
    void failedWriteIsKeptForTheNextFlush() {
        counters.add("a", 5, 5);
        clock.advance(Duration.ofHours(1));

        counters.flush(true, (hour, key, delta) -> {
            throw new IllegalStateException("expected by the test");
        });
        assertEquals(1, counters.size());

        counters.flush(true, this::write);
        assertEquals(List.of(HOUR + " a 5 5"), writes);
        assertEquals(0, counters.size());
    }

    @Test
    void rejectsTheWrongNumberOfAmounts() {
        assertThrows(IllegalArgumentException.class, () -> counters.add("a", 1));
        assertThrows(IllegalArgumentException.class, () -> new HourlyCounters<String>("none", 0, clock));
    }
}
