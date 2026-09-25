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
 * @param code    the name of the {@link Refusal} that refused this, where it has been given one,
 *         and {@code null} where it has not. It is what a reader quotes in a report and what an
 *         operator looks up to find the one line that threw, which is why it is a name and not a
 *         number: the compiler keeps it unique, and renaming it is a refactor rather than a
 *         migration
 * @param retryAfterSeconds how long to wait before asking again, on a refusal that named a time,
 *         and {@code null} on every other failure. It rides here as well as in the
 *         {@code Retry-After} header, because a header is only readable from another origin when
 *         the server says it may be, and a screen counting the wait down should not depend on that
 * @param reference a short code naming the log line this failure wrote, on a fault, and
 *         {@code null} on a refusal. It is the one thing a reader can hand an operator that finds
 *         the technical half of what happened, which is why the technical half is not in here
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseWrapper(
        String error, String message, String code, Long retryAfterSeconds, String reference) {
    public ErrorResponseWrapper(String error, String message, Long retryAfterSeconds) {
        this(error, message, null, retryAfterSeconds, null);
    }

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

    /**
     * Creates the error response for a refusal that has been given a name, which is the shape
     * every newly named refusal answers with.
     *
     * @param refusal what refused
     * @return the body, carrying the refusal's own sentence and its name as the code
     */
    public static ErrorResponseWrapper of(Refusal refusal) {
        return of(refusal, refusal.message());
    }

    /**
     * Creates the error response for a named refusal whose sentence names a detail.
     *
     * @param refusal what refused
     * @param message the sentence to show, which is the refusal's own unless a detail was named
     * @return the body, carrying the refusal's name as its code
     */
    public static ErrorResponseWrapper of(Refusal refusal, String message) {
        return of(refusal, message, null);
    }

    /**
     * Creates the error response for a named refusal that says when to come back.
     *
     * @param refusal what refused
     * @param message the sentence to show
     * @param retryAfterSeconds how long to wait before asking again
     * @return the body, carrying the refusal's name as its code
     */
    public static ErrorResponseWrapper of(Refusal refusal, String message, Long retryAfterSeconds) {
        return new ErrorResponseWrapper(
                refusal.status().getMessage(), message, refusal.name(), retryAfterSeconds, null);
    }
}
