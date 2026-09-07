/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import java.time.Instant;

/**
 * What a proven password earns: a session, or the one step that still stands between the account and
 * one.
 *
 * @param addressRequired whether the account has to be given an address mail can reach before there
 *                        can be a session. The one-time token for that step travels in {@code token},
 *                        the same way the forced password rotation carries its own.
 */
public record LoginResult(
        boolean success,
        String message,
        String token,
        Instant expiresAt,
        boolean passwordChangeRequired,
        boolean addressRequired,
        boolean twoFactorRequired,
        String preAuthToken,
        Instant preAuthTokenExpiresAt) {

    public static LoginResult failure(String message) {
        return new LoginResult(false, message, null, null, false, false, false, null, null);
    }

    public static LoginResult success(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, false, false, false, null, null);
    }

    public static LoginResult passwordChangeRequired(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, true, false, false, null, null);
    }

    public static LoginResult addressRequired(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, false, true, false, null, null);
    }

    public static LoginResult twoFactorRequired(String preAuthToken, Instant expiresAt) {
        return new LoginResult(true, null, null, null, false, false, true, preAuthToken, expiresAt);
    }
}
