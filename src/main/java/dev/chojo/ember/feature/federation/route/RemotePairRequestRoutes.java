/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes.StatusResponse;
import dev.chojo.ember.feature.federation.service.IncomingPairRequestService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Requests to federate between stations of two instances that are not partners yet.
 *
 * <p>No partnership exists while these are exchanged, so none of them carries a partner session.
 * What stands in for one is a pair of signatures inside each body: the station's federation key and
 * the discovery key of its instance, which the receiving instance knows from discovery. All three
 * stay callable across contract versions, like the handshake, so that two instances can still tell
 * each other why they cannot pair.
 */
@Singleton
public class RemotePairRequestRoutes implements Routes {

    public static final FederationEndpoint PAIR_REQUEST = FederationEndpoint.post(
                    FederationSurface.CORE, "/remote/pair-request", PairRequestMessage.class, PairRequestReceipt.class)
            .exempt();
    public static final FederationEndpoint PAIR_REQUEST_STATUS = FederationEndpoint.post(
                    FederationSurface.CORE,
                    "/remote/pair-request/status",
                    PairRequestStatusQuery.class,
                    PairRequestAnswer.class)
            .exempt();
    public static final FederationEndpoint PAIR_REQUEST_ANSWER = FederationEndpoint.post(
                    FederationSurface.CORE,
                    "/remote/pair-request/answer",
                    PairRequestAnswer.class,
                    StatusResponse.class)
            .exempt();

    public static final List<FederationEndpoint> CONTRACT =
            List.of(PAIR_REQUEST, PAIR_REQUEST_STATUS, PAIR_REQUEST_ANSWER);

    private final IncomingPairRequestService incoming;
    private final OutgoingPairRequestService outgoing;

    @Inject
    public RemotePairRequestRoutes(IncomingPairRequestService incoming, OutgoingPairRequestService outgoing) {
        this.incoming = incoming;
        this.outgoing = outgoing;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, binder -> binder.handle(PAIR_REQUEST, this::receive)
                .handle(PAIR_REQUEST_STATUS, this::status)
                .handle(PAIR_REQUEST_ANSWER, this::answer));
    }

    /** A station of another instance asks a station here to federate. */
    private void receive(Context ctx) {
        ctx.status(HttpStatus.CREATED).json(incoming.receive(ctx.bodyAsClass(PairRequestMessage.class)));
    }

    /** The instance that sent a request asks where it stands. */
    private void status(Context ctx) {
        ctx.json(incoming.status(ctx.bodyAsClass(PairRequestStatusQuery.class)));
    }

    /** The instance that was asked tells this one what its station answered. */
    private void answer(Context ctx) {
        outgoing.receiveAnswer(ctx.bodyAsClass(PairRequestAnswer.class));
        ctx.json(new StatusResponse("ok"));
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
            String instanceSignature) {}

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
            String instanceSignature) {}

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
            String instanceSignature) {}
}
