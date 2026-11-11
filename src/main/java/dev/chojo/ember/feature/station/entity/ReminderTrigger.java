/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * What sent a reminder to a station's manager.
 */
public enum ReminderTrigger {
    /** An instance administrator, by hand. */
    MANUAL,
    /** The automatic reminder a week after the station started waiting. */
    AFTER_7_DAYS,
    /** The automatic reminder three weeks after the station started waiting, and the last one. */
    AFTER_21_DAYS
}
