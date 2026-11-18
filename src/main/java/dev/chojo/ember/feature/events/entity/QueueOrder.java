/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/** How a waiting list orders the sign-ups that join it. Moving somebody by hand always wins over it. */
public enum QueueOrder {
    /** Oldest sign-up first. */
    TIME,
    /** Highest fairness score first, ties by sign-up time. */
    FAIRNESS
}
