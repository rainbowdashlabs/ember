/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

/** How a member's latest link request stands. */
public enum LinkStatus {
    /** The person has not answered yet and still may. */
    WAITING,
    /** The person linked their account to the member. */
    ACCEPTED,
    /** The person refused. */
    DECLINED,
    /** Thirty days passed without an answer. */
    EXPIRED
}
