/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * Identifies which kind of person identifier a column holds.
 */
public enum IdentityType {
    /** Integer FK to {@code account.id}. */
    ACCOUNT_ID,

    /** Integer FK to {@code station_member.id}. */
    MEMBER_ID,

    /** UUID matching {@code station_member.uid}, used in federation contexts. */
    MEMBER_UID
}
