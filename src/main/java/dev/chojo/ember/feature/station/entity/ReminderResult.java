/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * What a reminder sent by hand came to.
 */
public enum ReminderResult {
    /** The mail to the manager went out. */
    SENT,
    /**
     * This instance has no way of asking for an address to be confirmed and counts it as confirmed, so the
     * manager's address was confirmed and no mail went out.
     */
    CONFIRMED_WITHOUT_MAIL
}
