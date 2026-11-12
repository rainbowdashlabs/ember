/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/** Which dates an edit of a series applies to. */
public enum EditScopeKind {
    /** The whole series, changed in place. */
    ALL,
    /** Only one date, which becomes an appointment of its own. */
    THIS,
    /** One date and every one after it, which go on as a new series. */
    THIS_AND_LATER
}
