/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

/**
 * Wraps every cipher failure on the credential encrypt / decrypt path.
 */
public class CredentialCipherException extends RuntimeException {
    public CredentialCipherException(String message) {
        super(message);
    }

    public CredentialCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
