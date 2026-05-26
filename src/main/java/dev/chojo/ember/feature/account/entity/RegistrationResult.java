/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

/**
 * Result of a registration attempt.
 *
 * @param success whether the registration succeeded
 * @param message error message on failure, {@code null} on success
 * @param account the created account on success, {@code null} on failure
 */
public record RegistrationResult(boolean success, String message, Account account) {
    /**
     * Creates a failed registration result with an error message.
     */
    public static RegistrationResult failure(String message) {
        return new RegistrationResult(false, message, null);
    }

    /**
     * Creates a successful registration result with the created account.
     */
    public static RegistrationResult success(Account account) {
        return new RegistrationResult(true, null, account);
    }
}
