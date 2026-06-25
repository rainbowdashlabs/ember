/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

/**
 * Thrown when a backend is currently marked unhealthy (after a probe failure) or fails its
 * I/O mid-flight. Mapped centrally to HTTP 503 by the global exception handler so producers
 * never need to branch on backend type.
 */
public class StorageUnavailableException extends StorageException {
    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageUnavailableException(Throwable cause) {
        super(cause);
    }
}
