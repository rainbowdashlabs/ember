/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

/**
 * Why a member manager may find an account by its name, from the closest tie to the loosest. An account
 * reached in several ways is shown with the closest one.
 */
public enum FindableBecause {
    /** The account is an active member of a station the manager is an active member of. */
    SHARED_STATION,
    /** The account is an active member of a station in an association one of the manager's stations is in. */
    SHARED_ASSOCIATION,
    /** The account chose to be findable by name from every station of this instance. */
    OPTED_IN
}
