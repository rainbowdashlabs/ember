/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * The PAdES baseline level a sealed document reached.
 */
public enum SealLevel {
    /** Sealed without a timestamp: the signing time is only this server's clock. */
    BASELINE_B,
    /** Sealed with a timestamp from a timestamp service, which proves when the seal existed. */
    BASELINE_T,
    /**
     * Sealed with a timestamp and the material to check it later without asking anyone: every
     * certificate of the seal and of the timestamp, with revocation data for each one that needs it.
     */
    BASELINE_LT
}
