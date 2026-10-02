/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

/**
 * Whether a notification is written again while an identical one is still unread.
 *
 * <p>Every sender says which, so a reminder that is meant to repeat and a notice that is meant to
 * arrive once cannot be mistaken for each other.
 */
public enum Delivery {
    /** Written on every call, whatever is already waiting. */
    EVERY_TIME,
    /** Written only to people who have no unread notification of the same type and data. */
    ONCE_WHILE_UNREAD
}
