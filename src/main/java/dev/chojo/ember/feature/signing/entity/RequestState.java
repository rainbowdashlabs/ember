/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/** Where a request for signatures on one document stands. */
public enum RequestState {
    /** At least one field still waits for a signature. */
    OPEN,
    /** No field waits any more, and at least one was signed or confirmed on paper. */
    COMPLETE,
    /** No field waits any more, and none was signed or confirmed on paper. */
    WITHDRAWN,
    /** A corrected document replaced it; what was signed on it stays. */
    SUPERSEDED
}
