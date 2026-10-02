/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestReceipt;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

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
        var exchange = post(baseUrl, RemotePairRequestRoutes.PAIR_REQUEST, message);
        if (!(exchange instanceof Delivery.Answered answered) || !answered.successful()) return exchange;
        try {
            var receipt = mapper.readValue(answered.body(), PairRequestReceipt.class);
            return new Delivery.Taken(receipt.stationName());
        } catch (RuntimeException e) {
            log.warn("The answer of {} to a request to federate could not be read", baseUrl);
            return new Delivery.Answered(answered.status(), null, answered.body());
        }
    }

    private Delivery post(String baseUrl, FederationEndpoint endpoint, Object body) {
        String url = OutboundHttp.join(baseUrl, "/api/v1" + endpoint.path());
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            var response = outbound.send(request, HttpResponse.BodyHandlers.ofString());
            return new Delivery.Answered(response.statusCode(), refusalCode(response.body()), response.body());
        } catch (RefusedDestinationException e) {
            log.warn("Request to federate to {} refused: {}", url, e.getMessage());
            return new Delivery.Failed(Failure.ADDRESS_REFUSED);
        } catch (HttpTimeoutException e) {
            log.warn("Request to federate to {} timed out", url);
            return new Delivery.Failed(Failure.UNREACHABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Delivery.Failed(Failure.UNREACHABLE);
        } catch (Exception e) {
            log.warn("Request to federate to {} failed: {}", url, e.getMessage());
            return new Delivery.Failed(Failure.UNREACHABLE);
        }
    }

    private @Nullable String refusalCode(@Nullable String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode node = mapper.readTree(body);
            var code = node.path("code");
            return code.isString() ? code.asString() : null;
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
         * @param code   the code of its refusal, where it named one
         * @param body   what it said
         */
        record Answered(int status, @Nullable String code, String body) implements Delivery {
            public boolean successful() {
                return status >= 200 && status < 300;
            }
        }

        /** No answer could be had. */
        record Failed(Failure failure) implements Delivery {}
    }

    /** Why no answer could be had. */
    public enum Failure {
        /** The address is not one this instance calls. */
        ADDRESS_REFUSED,
        /** Nothing answered in time. */
        UNREACHABLE
    }
}
