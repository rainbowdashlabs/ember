/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/** What the instance's account list can be sorted by. Ties fall back to the name. */
public enum AccountSort {
    /** By register name. */
    NAME,
    /** By the last sign-in; accounts that never signed in come last either way. */
    LAST_SIGN_IN,
    /** By how many passkeys an account may sign in with. */
    PASSKEYS
}
