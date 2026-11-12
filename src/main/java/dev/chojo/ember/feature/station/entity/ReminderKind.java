/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * Which mail a reminder to a station's manager was.
 */
public enum ReminderKind {
    /** A fresh link to choose a password or to confirm the address, for a manager who cannot sign in yet. */
    SETUP_LINK,
    /** A note that the station waits to be set up, for a manager who can sign in. */
    SETUP_REMINDER
}
