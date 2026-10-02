/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * What a proven password earns: a session, or the one step that still stands between the account and
 * one.
 *
 * @param addressRequired whether the account has to be given an address mail can reach before there
 *                        can be a session. The one-time token for that step travels in {@code token},
 *                        the same way the forced password rotation carries its own.
 * @param oneTimePasswordExpired whether the sign-in was refused because the password was a one-time
 *                        password whose time is up. Only ever set after the password proved right, so
 *                        it tells a guesser nothing a correct password would not.
 */
public record LoginResult(
        boolean success,
        @Nullable String message,
        @Nullable String token,
        @Nullable Instant expiresAt,
        boolean passwordChangeRequired,
        boolean addressRequired,
        boolean twoFactorRequired,
        @Nullable String preAuthToken,
        @Nullable Instant preAuthTokenExpiresAt,
        boolean oneTimePasswordExpired) {

    public static LoginResult failure(String message) {
        return new LoginResult(false, message, null, null, false, false, false, null, null, false);
    }

    /**
     * A sign-in refused because the one-time password it used has expired.
     */
    public static LoginResult oneTimePasswordHasExpired() {
        return new LoginResult(
                false, "The one-time password has expired", null, null, false, false, false, null, null, true);
    }

    public static LoginResult success(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, false, false, false, null, null, false);
    }

    public static LoginResult passwordChangeRequired(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, true, false, false, null, null, false);
    }

    public static LoginResult addressRequired(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, false, true, false, null, null, false);
    }

    public static LoginResult twoFactorRequired(String preAuthToken, Instant expiresAt) {
        return new LoginResult(true, null, null, null, false, false, true, preAuthToken, expiresAt, false);
    }

    /**
     * Whether this result is a finished sign-in, as opposed to a refusal or one of the steps that
     * still stand in the way of one. Only a finished sign-in puts a session into the browser.
     */
    public boolean isSession() {
        return success && token != null && !passwordChangeRequired && !addressRequired && !twoFactorRequired;
    }
}
