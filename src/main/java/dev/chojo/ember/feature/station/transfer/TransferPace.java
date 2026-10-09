/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.transfer;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * How far apart the requests of one import go to the source, so a high-fanout import cannot overload
 * it. An installation always keeps the gap; only a harness that serves the source itself in the same
 * process has no source to protect and leaves it out.
 */
@Singleton
public final class TransferPace {
    private static final long DEFAULT_INTERVAL_MILLIS = 200L;

    private final long intervalMillis;

    /** The pace an installation imports at: at most five requests a second. */
    @Inject
    public TransferPace() {
        this(DEFAULT_INTERVAL_MILLIS);
    }

    private TransferPace(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    /**
     * @return a pace without a gap, for a source served in the same process
     */
    public static TransferPace unthrottled() {
        return new TransferPace(0L);
    }

    /**
     * @return the least time between two requests to the source, in milliseconds
     */
    public long intervalMillis() {
        return intervalMillis;
    }
}
