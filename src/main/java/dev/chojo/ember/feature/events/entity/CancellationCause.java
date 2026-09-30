/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/** Who called one date of an appointment off. */
public enum CancellationCause {
    /** A manager, who may have given a reason. */
    MANUAL,
    /**
     * The check that the minimum of accepted registrations was not reached in time. Its reason is
     * worded from the cause in the reader's language, so none is stored.
     */
    THRESHOLD
}
