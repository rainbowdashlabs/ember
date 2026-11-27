/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.entity;

/**
 * Up to which visibility a station calendar feed shows the station's appointments. The levels are
 * ordered: each one shows everything the one before it shows and more.
 */
public enum StationFeedLevel {
    /** What the station's public calendar shows: unrestricted appointments marked public. */
    PUBLIC,
    /** Every appointment without a view restriction, public or not. */
    MEMBERS,
    /** Every appointment of the station, restricted ones included. */
    ALL
}
