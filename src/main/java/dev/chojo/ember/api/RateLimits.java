/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.Optional;

/**
 * Turning a rate limiter's answer into a refusal the caller can act on.
 *
 * <p>One place rather than the five that held byte-identical copies of it, and one that actually
 * delivers the wait. The copies passed the seconds as the third argument to Javalin's
 * {@code HttpResponseException}, which is a details map and not headers, and nothing read it back
 * out: the number was worked out and thrown away, while the doc comment above each copy said a
 * {@code Retry-After} header was being sent.
 *
 * <p>The refusal carries the seconds instead, and the one exception handler that turns these into
 * JSON writes both the header and the body field. Setting it there rather than here is what makes
 * it impossible for a route to refuse without saying how long.
 */
public final class RateLimits {
    static final String MESSAGE = "Too many requests, please try again later";

    private RateLimits() {}

    /**
     * Refuses the request when the limiter said to, and does nothing when it did not.
     *
     * @param retryAfter what the limiter said, empty where it admitted the request
     * @throws TooManyRequestsException when the limiter refused
     */
    public static void enforce(Optional<Long> retryAfter) {
        if (retryAfter.isEmpty()) return;
        throw new TooManyRequestsException(retryAfter.get());
    }

    /** A refusal that knows how long it is asking for, so the answer can say it. */
    public static class TooManyRequestsException extends HttpResponseException {
        private final long retryAfterSeconds;

        TooManyRequestsException(long retryAfterSeconds) {
            super(HttpStatus.TOO_MANY_REQUESTS.getCode(), MESSAGE, Map.of());
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
