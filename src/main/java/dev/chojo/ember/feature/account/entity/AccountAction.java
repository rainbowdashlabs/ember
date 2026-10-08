/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/**
 * What a station, or a guardian at one, does to the account behind one of its members.
 *
 * <p>An account is the person's, and the station only holds a membership of it. Some of these stay
 * inside the station: a name the station shows, whether the member may sign in there, a setup mail
 * the person still needs. The others change the account's address or the ways it signs in, which
 * changes it for every other station and association the person belongs to as well. Every one is
 * listed here and decided in one place.
 */
public enum AccountAction {
    /** A member manager moving the account's address. */
    EMAIL_CHANGE(true),
    /** A member manager changing the name the account signs in with. */
    USERNAME_CHANGE(true),
    /** A station administrator clearing the account's second factor. */
    SECOND_FACTOR_RESET(true),
    /** A member manager disabling the passkeys and sending a fresh setup link. */
    ONBOARD_AGAIN(true),
    /** A member manager resetting the password, optionally forcing a change. */
    PASSWORD_RESET(true),
    /** A member manager handing out a passkey code in the room. */
    PASSKEY_CODE(true),
    /** A station administrator issuing a one-time password. */
    ONE_TIME_PASSWORD(true),
    /** A guardian setting the address of a member in their care. */
    GUARDIAN_EMAIL(true),
    /** A guardian setting the password of a member in their care. */
    GUARDIAN_PASSWORD(true),
    /** A guardian setting the name a member in their care signs in with. */
    GUARDIAN_USERNAME(true),
    /** A guardian handing out a passkey code for a member in their care. */
    GUARDIAN_PASSKEY_CODE(true),
    /** A member manager granting sign-in, or sending the setup mail of an account not set up yet. */
    SETUP_MAIL(false),
    /** A member manager correcting the account's name. */
    RENAME(false),
    /** A member manager approving the name the person asked for. */
    NAME_CHANGE_APPROVAL(false),
    /** A member manager deleting the member, which takes an account along that has no other membership. */
    MEMBER_DELETE(false),
    /** A guardian switching sign-in on or off for a member in their care. */
    GUARDIAN_LOGIN(false);

    private final boolean reachesPastStation;

    AccountAction(boolean reachesPastStation) {
        this.reachesPastStation = reachesPastStation;
    }

    /**
     * Whether the action changes the account's address or the ways it signs in, which reaches every
     * other station and association the person belongs to.
     *
     * @return true for an action on the address or the credentials
     */
    public boolean reachesPastStation() {
        return reachesPastStation;
    }
}
