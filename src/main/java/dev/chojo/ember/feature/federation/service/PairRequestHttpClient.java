/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.PairRequestReason;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestReceipt;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestStatusQuery;
import dev.chojo.ember.feature.federation.service.OutboundHttp.JsonPost;
import dev.chojo.ember.feature.federation.service.OutboundHttp.PostFailure;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Carries the messages about a request to federate to the other instance.
 *
 * <p>Unlike the partner client this one reports how a call ended rather than folding every failure
 * into nothing: a person is waiting on a request, and "the other instance runs an older Ember" and
 * "the other instance did not answer" ask them to do different things.
 */
@Singleton
public class PairRequestHttpClient {
    private static final Logger log = LoggerFactory.getLogger(PairRequestHttpClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final OutboundHttp outbound;
    private final JsonMapper mapper = OutboundHttp.lenientMapper();

    @Inject
    public PairRequestHttpClient(OutboundHttp outbound) {
        this.outbound = outbound;
    }

    /**
     * Sends a request to the instance of the station it asks.
     *
     * @param baseUrl where that instance is reached
     * @param message the signed request
     * @return how the call ended
     */
    public Delivery send(String baseUrl, PairRequestMessage message) {
        var exchange = post(baseUrl, PairRequestRoutes.PAIR_REQUEST, message);
        if (!(exchange instanceof Delivery.Answered answered) || !answered.successful()) return exchange;
        try {
            var receipt = mapper.readValue(answered.body(), PairRequestReceipt.class);
            return new Delivery.Taken(receipt.stationName());
        } catch (RuntimeException e) {
            log.warn("The answer of {} to a request to federate could not be read", baseUrl);
            return new Delivery.Answered(answered.status(), null, answered.body());
        }
    }

    /**
     * Asks the instance a request was sent to where it stands.
     *
     * @param baseUrl where that instance is reached
     * @param query   the signed question
     * @return the answer, or empty where none could be had
     */
    public Optional<PairRequestAnswer> askStatus(String baseUrl, PairRequestStatusQuery query) {
        if (!(post(baseUrl, PairRequestRoutes.PAIR_REQUEST_STATUS, query) instanceof Delivery.Answered answered)
                || !answered.successful()) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(answered.body(), PairRequestAnswer.class));
        } catch (RuntimeException e) {
            log.warn("The status of a request to federate from {} could not be read", baseUrl);
            return Optional.empty();
        }
    }

    /**
     * Tells the instance that sent a request what its station answered.
     *
     * @param baseUrl where that instance is reached
     * @param answer  the signed answer
     * @return true when that instance took it
     */
    public boolean deliverAnswer(String baseUrl, PairRequestAnswer answer) {
        return post(baseUrl, PairRequestRoutes.PAIR_REQUEST_ANSWER, answer) instanceof Delivery.Answered answered
                && answered.successful();
    }

    private Delivery post(String baseUrl, String path, Object body) {
        String url = OutboundHttp.join(baseUrl, "/api/v1" + path);
        return switch (outbound.postJson(url, mapper.writeValueAsString(body), TIMEOUT)) {
            case JsonPost.Answered answered ->
                new Delivery.Answered(answered.status(), reasonIn(answered.body()), answered.body());
            case JsonPost.Failed failed -> {
                log.warn("A message about a request to federate did not reach {}: {}", url, failed.failure());
                yield new Delivery.Failed(failed.failure());
            }
        };
    }

    /**
     * The reason the other instance named for its refusal. A name outside the closed set reads as
     * none, the same as a body that names nothing.
     */
    private @Nullable PairRequestReason reasonIn(@Nullable String body) {
        if (body == null || body.isBlank()) return null;
        try {
            var reason = mapper.readTree(body).path("reason");
            if (!reason.isString()) return null;
            String named = reason.asString();
            return Arrays.stream(PairRequestReason.values())
                    .filter(value -> value.name().equals(named))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    /** How a call to the other instance ended. */
    public sealed interface Delivery {
        /** The other instance took the request and named the station it asks. */
        record Taken(String stationName) implements Delivery {}

        /**
         * The other instance answered.
         *
         * @param status what it answered with
         * @param reason why it refused, where it named a reason
         * @param body   what it said
         */
        record Answered(int status, @Nullable PairRequestReason reason, String body) implements Delivery {
            public boolean successful() {
                return status >= 200 && status < 300;
            }
        }

        /** No answer could be had. */
        record Failed(PostFailure failure) implements Delivery {}
    }
}
