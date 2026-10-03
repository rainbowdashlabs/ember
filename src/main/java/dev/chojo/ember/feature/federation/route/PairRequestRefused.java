/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.PairRequestReason;
import org.jspecify.annotations.Nullable;

/**
 * A refusal of a message about a request to federate, answered with its reason on the wire beside the
 * usual code, so the other instance reads why without knowing this instance's refusal codes.
 */
public class PairRequestRefused extends RefusalResponse {
    private final PairRequestReason reason;
    private final @Nullable Long retryAfterSeconds;

    /**
     * The same refusal, answered with its reason.
     *
     * @param refused what refused the message
     * @param reason  the reason it goes out under
     */
    PairRequestRefused(RefusalResponse refused, PairRequestReason reason) {
        super(refused.refusal(), refused.getMessage(), refused.detail());
        this.reason = reason;
        this.retryAfterSeconds =
                refused instanceof RateLimits.TooManyRequestsException limited ? limited.retryAfterSeconds() : null;
    }

    /**
     * How long the other instance is asked to wait, for a refusal that named a time.
     *
     * @return the wait in whole seconds, or {@code null}
     */
    public @Nullable Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public Object body() {
        var refusal = refusal();
        return new PairRequestRefusal(
                refusal.status().getMessage(), getMessage(), refusal.code(), reason, retryAfterSeconds);
    }

    /**
     * What a pair-request endpoint answers a refused message with.
     *
     * @param error             the error category
     * @param message           the refusal's sentence
     * @param code              the refusal's code on this instance
     * @param reason            why the message was refused, from a closed set every instance reads
     * @param retryAfterSeconds how long to wait before asking again, on a refusal that named a time
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PairRequestRefusal(
            String error,
            String message,
            String code,
            PairRequestReason reason,
            @Nullable Long retryAfterSeconds) {}
}
