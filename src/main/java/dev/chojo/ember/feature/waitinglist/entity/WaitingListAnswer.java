/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

/**
 * The three things somebody can say back to an invitation.
 *
 * <p>All three are one click on the entry's own page, without signing in. Somebody with no interest
 * will not set a password and walk through an account just to say no, and if that is the only route
 * the station never learns.
 */
public enum WaitingListAnswer {
    /** They are coming, so the station knows to expect them. */
    COMING,
    /** Not coming, and not to be asked again. */
    NOT_INTERESTED,
    /** Still interested; the date is the problem and the station should offer another. */
    DATE_DOES_NOT_SUIT
}
