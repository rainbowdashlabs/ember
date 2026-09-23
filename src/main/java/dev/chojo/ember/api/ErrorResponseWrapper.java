/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard JSON error response sent to clients when a request fails.
 *
 * @param error   the error category (e.g. "Invalid Input", "Internal Server Error")
 * @param message a human-readable description of the error, or {@code null}
 * @param retryAfterSeconds how long to wait before asking again, on a refusal that named a time,
 *         and {@code null} on every other failure. It rides here as well as in the
 *         {@code Retry-After} header, because a header is only readable from another origin when
 *         the server says it may be, and a screen counting the wait down should not depend on that
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseWrapper(String error, String message, Long retryAfterSeconds) {
    public ErrorResponseWrapper(String error, String message) {
        this(error, message, null);
    }

    /**
     * Creates an error response with no detail message.
     *
     * @param error the error category
     */
    public ErrorResponseWrapper(String error) {
        this(error, null);
    }
}
