/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A UTC clock that stands still until a test moves it.
 */
public final class MovableClock extends Clock {
    private Instant now;

    /**
     * Creates a clock standing at the given instant.
     *
     * @param start where the clock stands
     */
    public MovableClock(Instant start) {
        this.now = start;
    }

    /**
     * Moves the clock forward.
     *
     * @param duration how far
     */
    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
