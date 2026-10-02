/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.PairRequestRepository;
import dev.chojo.ember.feature.federation.service.PairRequestHttpClient.Delivery;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.HttpStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Requests to federate that stations here send to stations of other instances.
 *
 * <p>The request is signed by the station and by this instance, sent, and written down only once the
 * other instance has taken it, so a request that never arrived is never shown as waiting. The other
 * instance's refusal reaches the person who sent it under its own name; an instance that predates
 * these requests is told apart from one that refused, because only the second can be asked again.
 */
@Singleton
public class OutgoingPairRequestService {
    private static final Logger log = LoggerFactory.getLogger(OutgoingPairRequestService.class);

    private final PairRequestRepository requests;
    private final FederationRepository partners;
    private final StationRepository stations;
    private final PairRequestPeers peers;
    private final PairRequestSignatures signatures;
    private final PairRequestHttpClient httpClient;
    private final StationSigner signer;
    private final RemoteUrlValidator urlValidator;
    private final String localBaseUrl;

    @Inject
    public OutgoingPairRequestService(
            PairRequestRepository requests,
            FederationRepository partners,
            StationRepository stations,
            PairRequestPeers peers,
            PairRequestSignatures signatures,
            PairRequestHttpClient httpClient,
            StationSigner signer,
            RemoteUrlValidator urlValidator,
            Api apiConfig) {
        this.requests = requests;
        this.partners = partners;
        this.stations = stations;
        this.peers = peers;
        this.signatures = signatures;
        this.httpClient = httpClient;
        this.signer = signer;
        this.urlValidator = urlValidator;
        this.localBaseUrl = stripTrailingSlash(apiConfig.baseUrl());
    }

    /**
     * Sends a request to a station of another instance that the discovery page lists.
     *
     * @param stationId the asking station here
     * @param target    the asked station and the instance it lives on
     * @return the request, waiting for its answer
     */
    public PairRequest send(int stationId, RemoteTarget target) {
        var station = stations.findById(stationId).orElseThrow(FederationRefusal.FEDERATION_STATION_NOT_HERE::raise);
        if (station.uid().equals(target.stationUid())) {
            throw FederationRefusal.PAIR_REQUEST_TO_OWN_STATION.raise();
        }
        requireNothingStanding(stationId, target.stationUid());
        if (!urlValidator.isAllowed(target.baseUrl())) {
            throw FederationRefusal.PAIR_REQUEST_PEER_ADDRESS_REFUSED.raise();
        }

        var message = signatures.request(
                stationId,
                station.uid(),
                station.name(),
                signer.ensurePublicKey(stationId),
                localBaseUrl,
                target.stationUid(),
                FederationContractVersions.current());
        var delivery = httpClient.send(target.baseUrl(), message);
        if (!(delivery instanceof Delivery.Taken taken)) {
            throw refusalFor(delivery).raise();
        }
        var request = requests.recordOutgoing(
                stationId, target.stationUid(), taken.stationName(), target.baseUrl(), target.instanceKey());
        log.info(
                "Station {} asked station {} on {} to federate (request {})",
                stationId,
                target.stationUid(),
                target.baseUrl(),
                request.id());
        return request;
    }

    /**
     * Sends a request to the station a pairing code of another instance names. The instance has to
     * be one this instance knows from discovery, since only its discovery key can vouch for the
     * answer.
     *
     * @param stationId  the asking station here
     * @param stationUid the station the code names
     * @param address    the address the code names, host and port
     * @return the request, waiting for its answer
     */
    public PairRequest sendToCode(int stationId, UUID stationUid, String address) {
        var peer = peers.atAddress(address).orElseThrow(FederationRefusal.PAIR_REQUEST_INSTANCE_NOT_KNOWN_HERE::raise);
        return send(stationId, new RemoteTarget(stationUid, peer.baseUrl(), peer.publicKey()));
    }

    private void requireNothingStanding(int stationId, UUID targetStationUid) {
        if (partners.findPartnerByStationAndRemoteUid(stationId, targetStationUid)
                .isPresent()) {
            throw DiscoveryRefusal.ALREADY_FEDERATED.raise();
        }
        var earlier = requests.find(stationId, PairRequestDirection.OUTGOING, targetStationUid);
        if (earlier.isEmpty()) return;
        if (earlier.get().status() == PairRequestStatus.PENDING) {
            throw DiscoveryRefusal.FEDERATION_REQUEST_ALREADY_SENT.raise();
        }
        if (earlier.get().coolingDown(Instant.now())) {
            throw FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY.raise();
        }
    }

    /**
     * The refusal the asking person is shown for a request the other instance did not take. Its own
     * refusal where it named one of the request refusals, and otherwise what the failure says about
     * the other instance: a route it does not have is an instance that predates these requests.
     */
    static Refusal refusalFor(Delivery delivery) {
        return switch (delivery) {
            case Delivery.Taken _ -> throw new IllegalStateException("A request that was taken is not a refusal");
            case Delivery.Failed failed ->
                switch (failed.failure()) {
                    case ADDRESS_REFUSED -> FederationRefusal.PAIR_REQUEST_PEER_ADDRESS_REFUSED;
                    case UNREACHABLE -> FederationRefusal.PAIR_REQUEST_PEER_UNREACHABLE;
                };
            case Delivery.Answered answered ->
                requestRefusal(answered.code())
                        .orElseGet(() -> answered.status() == HttpStatus.NOT_FOUND.getCode()
                                        || answered.status() == HttpStatus.METHOD_NOT_ALLOWED.getCode()
                                ? FederationRefusal.PAIR_REQUEST_PEER_TOO_OLD
                                : FederationRefusal.PAIR_REQUEST_REFUSED_BY_PEER);
        };
    }

    private static Optional<Refusal> requestRefusal(@Nullable String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(FederationRefusal.values())
                .filter(refusal -> refusal.name().startsWith("PAIR_REQUEST_"))
                .filter(refusal -> refusal.code().equals(code))
                .map(Refusal.class::cast)
                .findFirst();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * A station of another instance a request can be sent to.
     *
     * @param stationUid  the station
     * @param baseUrl     where its instance is reached
     * @param instanceKey its instance's discovery key, which every answer must be signed with
     */
    public record RemoteTarget(UUID stationUid, String baseUrl, String instanceKey) {}
}
