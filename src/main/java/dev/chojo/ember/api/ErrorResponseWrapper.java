/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

/**
 * Standard JSON error response sent to clients when a request fails.
 *
 * @param error   the error category (e.g. "Invalid Input", "Internal Server Error")
 * @param message a human-readable description of the error, or {@code null}
 */
public record ErrorResponseWrapper(String error, String message) {
    /**
     * Creates an error response with no detail message.
     *
     * @param error the error category
     */
    public ErrorResponseWrapper(String error) {
        this(error, null);
    }
}
