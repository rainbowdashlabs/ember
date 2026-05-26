/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import java.time.Instant;

/**
 * Result of a login attempt.
 *
 * @param success                whether the login succeeded
 * @param message                error message on failure, {@code null} on success
 * @param token                  the session or password change token on success
 * @param expiresAt              when the token expires
 * @param passwordChangeRequired whether a password change is required before a session can be created
 */
public record LoginResult(
        boolean success, String message, String token, Instant expiresAt, boolean passwordChangeRequired) {
    /**
     * Creates a failed login result with an error message.
     */
    public static LoginResult failure(String message) {
        return new LoginResult(false, message, null, null, false);
    }

    /**
     * Creates a successful login result with a session token.
     */
    public static LoginResult success(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, false);
    }

    /**
     * Creates a login result indicating a forced password change is required.
     */
    public static LoginResult passwordChangeRequired(String token, Instant expiresAt) {
        return new LoginResult(true, null, token, expiresAt, true);
    }
}
