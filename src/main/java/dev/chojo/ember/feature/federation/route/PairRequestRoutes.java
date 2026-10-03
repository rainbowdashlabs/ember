/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.PairRequestReason;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.route.PairRequestRefused.PairRequestRefusal;
import dev.chojo.ember.feature.federation.service.IncomingPairRequestService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Requests to federate between stations of two instances that are not partners yet.
 *
 * <p>They belong to discovery and sit beside its ping, not under {@code /remote}: no partnership
 * exists while they are exchanged, and they are not part of the versioned federation contract, so a
 * new kind of request never pauses partnerships that already stand. What stands in for a partner
 * session is a pair of signatures inside each body: the station's federation key and the discovery
 * key of its instance, which the receiving instance knows from discovery.
 */
@Singleton
public class PairRequestRoutes implements Routes {

    /** Where a station of another instance sends its request, below the API prefix. */
    public static final String PAIR_REQUEST = "/public/discovery/pair-request";

    /** Where the asking instance asks about its request. */
    public static final String PAIR_REQUEST_STATUS = "/public/discovery/pair-request/status";

    /** Where the asked instance pushes its station's answer. */
    public static final String PAIR_REQUEST_ANSWER = "/public/discovery/pair-request/answer";

    private final IncomingPairRequestService incoming;
    private final OutgoingPairRequestService outgoing;

    @Inject
    public PairRequestRoutes(IncomingPairRequestService incoming, OutgoingPairRequestService outgoing) {
        this.incoming = incoming;
        this.outgoing = outgoing;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + PAIR_REQUEST, this::receive);
        routes.post(prefix + PAIR_REQUEST_STATUS, this::status);
        routes.post(prefix + PAIR_REQUEST_ANSWER, this::answer);
    }

    /** A station of another instance asks a station here to federate. */
    @OpenApi(
            path = "/api/v1/public/discovery/pair-request",
            methods = HttpMethod.POST,
            summary = "Receive a signed request to federate from a station of another instance",
            tags = {"Discovery"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PairRequestMessage.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = PairRequestReceipt.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = PairRequestRefusal.class))
            })
    private void receive(Context ctx) {
        var message = ctx.bodyAsClass(PairRequestMessage.class);
        ctx.status(HttpStatus.CREATED).json(withReason(ctx, () -> incoming.receive(message)));
    }

    /** The instance that sent a request asks where it stands. */
    @OpenApi(
            path = "/api/v1/public/discovery/pair-request/status",
            methods = HttpMethod.POST,
            summary = "Answer the asking instance where its request to federate stands",
            tags = {"Discovery"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PairRequestStatusQuery.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PairRequestAnswer.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = PairRequestRefusal.class))
            })
    private void status(Context ctx) {
        var query = ctx.bodyAsClass(PairRequestStatusQuery.class);
        ctx.json(withReason(ctx, () -> incoming.status(query)));
    }

    /** The instance that was asked tells this one what its station answered. */
    @OpenApi(
            path = "/api/v1/public/discovery/pair-request/answer",
            methods = HttpMethod.POST,
            summary = "Receive the signed answer to a request to federate",
            tags = {"Discovery"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PairRequestAnswer.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = PairRequestRefusal.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = PairRequestRefusal.class))
            })
    private void answer(Context ctx) {
        var answer = ctx.bodyAsClass(PairRequestAnswer.class);
        ctx.json(withReason(ctx, () -> {
            outgoing.receiveAnswer(answer);
            return new MessageResponse("Answer taken");
        }));
    }

    /**
     * Runs the service call and turns a refusal it raises into one that names its
     * {@link PairRequestReason}, which is what the other instance reads.
     */
    private static <T> T withReason(Context ctx, Supplier<T> call) {
        try {
            return call.get();
        } catch (RefusalResponse refused) {
            var reason = PairRequestReason.of(refused.refusal());
            if (reason.isEmpty()) throw refused;
            var answered = new PairRequestRefused(refused, reason.get());
            var wait = answered.retryAfterSeconds();
            if (wait != null) ctx.header("Retry-After", Long.toString(wait));
            throw answered;
        }
    }

    /**
     * A station's request to federate with a station of another instance.
     *
     * @param requesterStationUid  the asking station
     * @param requesterStationName its name, shown to the asked station as text
     * @param requesterPublicKey   its federation key, which the partnership is verified with later
     * @param requesterBaseUrl     where its instance is reached, with the port
     * @param requesterInstanceKey the discovery key of its instance
     * @param targetStationUid     the station asked
     * @param contract             the contract the asking instance speaks
     * @param issuedAt             when the request was signed
     * @param nonce                a value used once, so the request cannot be sent twice
     * @param stationSignature     the asking station's signature over all of the above
     * @param instanceSignature    the asking instance's discovery signature over all of the above
     */
    public record PairRequestMessage(
            UUID requesterStationUid,
            String requesterStationName,
            String requesterPublicKey,
            String requesterBaseUrl,
            String requesterInstanceKey,
            UUID targetStationUid,
            FederationContract contract,
            Instant issuedAt,
            String nonce,
            String stationSignature,
            String instanceSignature) {

        /**
         * The same request carrying the given signatures.
         *
         * @param stationSignature  the asking station's signature
         * @param instanceSignature the asking instance's discovery signature
         * @return the signed request
         */
        public PairRequestMessage withSignatures(String stationSignature, String instanceSignature) {
            return new PairRequestMessage(
                    requesterStationUid,
                    requesterStationName,
                    requesterPublicKey,
                    requesterBaseUrl,
                    requesterInstanceKey,
                    targetStationUid,
                    contract,
                    issuedAt,
                    nonce,
                    stationSignature,
                    instanceSignature);
        }
    }

    /**
     * What the asked instance answers once it has taken a request.
     *
     * @param stationName the asked station's name, which the asking side shows with its request
     */
    public record PairRequestReceipt(String stationName) {}

    /**
     * The asking instance's question about where its request stands.
     *
     * @param requesterStationUid the asking station
     * @param targetStationUid    the station asked
     * @param issuedAt            when the question was signed
     * @param nonce               a value used once
     * @param stationSignature    the asking station's signature, checked against the key it sent with the request
     * @param instanceSignature   the asking instance's discovery signature
     */
    public record PairRequestStatusQuery(
            UUID requesterStationUid,
            UUID targetStationUid,
            Instant issuedAt,
            String nonce,
            String stationSignature,
            String instanceSignature) {

        /**
         * The same question carrying the given signatures.
         *
         * @param stationSignature  the asking station's signature
         * @param instanceSignature the asking instance's discovery signature
         * @return the signed question
         */
        public PairRequestStatusQuery withSignatures(String stationSignature, String instanceSignature) {
            return new PairRequestStatusQuery(
                    requesterStationUid, targetStationUid, issuedAt, nonce, stationSignature, instanceSignature);
        }
    }

    /**
     * The asked station's answer, pushed to the asking instance and handed back when it asks.
     *
     * @param requesterStationUid the asking station
     * @param targetStationUid    the station asked
     * @param status              where the request stands
     * @param stationName         the asked station's name
     * @param baseUrl             where the asked instance is reached
     * @param publicKey           the asked station's federation key, sent once it accepted
     * @param contract            the contract the asked instance speaks, sent once it accepted
     * @param issuedAt            when the answer was signed
     * @param nonce               a value used once
     * @param stationSignature    the asked station's signature, sent once it accepted
     * @param instanceSignature   the asked instance's discovery signature
     */
    public record PairRequestAnswer(
            UUID requesterStationUid,
            UUID targetStationUid,
            PairRequestStatus status,
            String stationName,
            String baseUrl,
            @Nullable String publicKey,
            @Nullable FederationContract contract,
            Instant issuedAt,
            String nonce,
            @Nullable String stationSignature,
            String instanceSignature) {

        /**
         * The same answer carrying the given signatures.
         *
         * @param stationSignature  the asked station's signature, for an acceptance only
         * @param instanceSignature the asked instance's discovery signature
         * @return the signed answer
         */
        public PairRequestAnswer withSignatures(@Nullable String stationSignature, String instanceSignature) {
            return new PairRequestAnswer(
                    requesterStationUid,
                    targetStationUid,
                    status,
                    stationName,
                    baseUrl,
                    publicKey,
                    contract,
                    issuedAt,
                    nonce,
                    stationSignature,
                    instanceSignature);
        }
    }
}
