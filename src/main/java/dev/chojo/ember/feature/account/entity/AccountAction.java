/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/**
 * What a station, or a guardian at one, does to the account behind one of its members.
 *
 * <p>Every one of these reaches past the station: an account is the person's, and the station only
 * holds a membership of it. Changing its address or the ways it signs in changes it for every other
 * station and association the person belongs to as well, which is why those are listed here one by
 * one and decided in one place.
 */
public enum AccountAction {
    /** A member manager moving the account's address. */
    EMAIL_CHANGE,
    /** A station administrator clearing the account's second factor. */
    SECOND_FACTOR_RESET,
    /** A member manager disabling the passkeys and sending a fresh setup link. */
    ONBOARD_AGAIN,
    /** A member manager resetting the password, optionally forcing a change. */
    PASSWORD_RESET,
    /** A member manager handing out a passkey code in the room. */
    PASSKEY_CODE,
    /** A station administrator issuing a one-time password. */
    ONE_TIME_PASSWORD,
    /** A guardian setting the address of a member in their care. */
    GUARDIAN_EMAIL,
    /** A guardian setting the password of a member in their care. */
    GUARDIAN_PASSWORD,
    /** A guardian handing out a passkey code for a member in their care. */
    GUARDIAN_PASSKEY_CODE
}
