/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

/**
 * A signing key that cannot be wrapped or unwrapped, or a wrap that cannot start because the at-rest
 * secret is missing or malformed. Never carries key material in its message.
 */
public class SigningKeyWrapException extends RuntimeException {
    /** @param message what went wrong, in plain words */
    public SigningKeyWrapException(String message) {
        super(message);
    }

    /**
     * @param message what went wrong, in plain words
     * @param cause   the failure underneath
     */
    public SigningKeyWrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
