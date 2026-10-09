/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

/**
 * A timestamp that does not chain to the root pinned for the service that gave it, so it proves nothing
 * and the next service is asked instead.
 */
final class UntrustedTimestampException extends RuntimeException {
    /**
     * @param message what did not hold, in plain words
     * @param cause   the failure underneath
     */
    UntrustedTimestampException(String message, Throwable cause) {
        super(message, cause);
    }
}
