/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * How far a station has come, as the instance administration sees it. Derived from the station's manager
 * and its setup, never stored.
 */
public enum StationState {
    /** Nobody with an account administers the station, so there is nobody to set it up. */
    NO_MANAGER,
    /** The manager has not chosen a password yet or has not confirmed their address. */
    WAITING_FOR_MANAGER,
    /** The manager can sign in, but the station's setup is not finished. */
    SETTING_UP,
    /** The station's setup is finished. */
    SET_UP;

    /**
     * Whether a station in this state waits for its manager, so that a reminder could help.
     *
     * @return {@code true} for a station whose manager still has something to do
     */
    public boolean waitsForManager() {
        return this == WAITING_FOR_MANAGER || this == SETTING_UP;
    }
}
