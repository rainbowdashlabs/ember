/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;

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
 * it impossible for a route to refuse without saying how long. Each limited route names its own
 * refusal, so a report says which limit it ran into.
 */
public final class RateLimits {
    private RateLimits() {}

    /**
     * Refuses the request when the limiter said to, and does nothing when it did not.
     *
     * @param refusal    what the route refuses with when the limit is reached
     * @param retryAfter what the limiter said, empty where it admitted the request
     * @throws TooManyRequestsException when the limiter refused
     */
    public static void enforce(Refusal refusal, Optional<Long> retryAfter) {
        if (retryAfter.isEmpty()) return;
        throw new TooManyRequestsException(refusal, retryAfter.get());
    }

    /** A refusal that knows how long it is asking for, so the answer can say it. */
    public static class TooManyRequestsException extends RefusalResponse {
        private final long retryAfterSeconds;

        TooManyRequestsException(Refusal refusal, long retryAfterSeconds) {
            super(refusal, refusal.message());
            this.retryAfterSeconds = retryAfterSeconds;
        }

        /**
         * How long the caller is asked to wait before trying again.
         *
         * @return the wait in whole seconds
         */
        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        @Override
        public Object body() {
            return ErrorResponseWrapper.of(refusal(), getMessage(), retryAfterSeconds);
        }
    }
}
