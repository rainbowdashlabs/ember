/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * What whoever runs an appointment chose for somebody they let in while every place is taken. A member
 * answering for themselves or their household has no choice: they join the waiting list.
 */
public enum WhenFull {
    /** Onto the waiting list, like a member signing up. */
    QUEUE,
    /** Accepted beyond the limit, the count then shows more people than places. */
    OVER_LIMIT
}
