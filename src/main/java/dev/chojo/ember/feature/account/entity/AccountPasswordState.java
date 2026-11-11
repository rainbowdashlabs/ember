/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/** How an account's password stands, as the instance's account list tells them apart and filters by. */
public enum AccountPasswordState {
    /** The account holds no password at all. */
    NONE,
    /** It holds a password that works on the login screen. */
    ON,
    /** It holds a password that no longer works on the login screen. */
    OFF,
    /** It holds a one-time password an administrator issued. */
    ONE_TIME
}
