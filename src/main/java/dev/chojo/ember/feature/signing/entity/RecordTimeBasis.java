/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/** Where the times of a signed document come from, which its record page states in words. */
public enum RecordTimeBasis {
    /** The seal carries a timestamp from an independent timestamp service. */
    TIMESTAMP_SERVICE,
    /**
     * Timestamps are switched on, but no service answered when the document was sealed: the times are only
     * the server's clock until a timestamp is added later.
     */
    NO_SERVICE_ANSWERED,
    /** The installation asks no timestamp service: the times are only the server's clock. */
    TIMESTAMPS_OFF
}
