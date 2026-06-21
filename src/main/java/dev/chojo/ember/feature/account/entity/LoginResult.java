/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import java.time.Instant;

public record LoginResult(
        boolean success,
        String message,
        String token,
        Instant expiresAt,
        boolean passwordChangeRequired,
        boolean twoFactorRequired,
        String preAuthToken,
        Instant preAuthTokenExpiresAt) {

    public static LoginResult failure(String message) {
        return new LoginResult(false, message, null, null, false, false, null, null);
    }

    public static LoginResult success(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, false, false, null, null);
    }

    public static LoginResult passwordChangeRequired(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, true, false, null, null);
    }

    public static LoginResult twoFactorRequired(String preAuthToken, Instant expiresAt) {
        return new LoginResult(true, null, null, null, false, true, preAuthToken, expiresAt);
    }
}
