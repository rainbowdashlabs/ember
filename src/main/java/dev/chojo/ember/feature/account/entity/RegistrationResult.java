/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import dev.chojo.ember.api.auth.InstanceUserType;

/**
 * Result of a registration attempt.
 *
 * @param success whether the registration succeeded
 * @param message error message on failure, {@code null} on success
 * @param account the created account on success, an opaque echo account on a
 *                masked-success response, {@code null} on failure
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

    /**
     * Creates a "masked" success result returned when the email is already in use.
     * The caller cannot distinguish this from a real success because the failure
     * path leaks whether the email exists; an out-of-band notification email is
     * sent to the existing owner instead.
     */
    public static RegistrationResult maskedSuccess(String email, String firstName, String lastName) {
        return new RegistrationResult(
                true, null, new Account(0, email, firstName, lastName, false, InstanceUserType.USER, ""));
    }
}
