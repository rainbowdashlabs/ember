/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

/**
 * Which way round a sharing row points. A share is opt-in, so the absence of any row means the gear
 * is not offered; a {@link #WITHHOLD} row exists to take one piece back out of a wider offer, and on
 * its own it does nothing, because there is nothing to take it out of.
 */
public enum ShareGrant {
    /** The gear this row names is on offer. */
    GRANT,
    /** The gear this row names is held back, whatever a wider row says about it. */
    WITHHOLD
}
