/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/** Whom a station chat is for, which decides who its members are. */
public enum ChatAudience {
    /** Everybody at the station who can use messages, trial members left out. */
    STATION,
    /** The members of one group of the station. */
    GROUP,
    /** The members with one tag of the station. */
    TAG
}
