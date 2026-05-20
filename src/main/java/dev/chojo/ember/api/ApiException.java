/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpStatus;

/**
 * Application-level exception that carries an HTTP status code.
 * Thrown from route handlers and translated into a JSON error response by the exception handler.
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    /**
     * Creates an API exception with the given HTTP status and message.
     *
     * @param status  the HTTP status to return to the client
     * @param message the error message
     */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status associated with this exception.
     *
     * @return the HTTP status
     */
    public HttpStatus status() {
        return status;
    }
}
