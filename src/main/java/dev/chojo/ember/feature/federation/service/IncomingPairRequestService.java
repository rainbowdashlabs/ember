/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.auth.signing.SignedRequests;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.PairRequestRepository;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestReceipt;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestStatusQuery;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.lifecycle.TaskScheduler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Requests to federate that stations of other instances send to stations here.
 *
 * <p>The sender is a stranger to every station here, so the request is taken only after the checks
 * a partnership would otherwise make unnecessary: both signatures, an instance this one knows from
 * discovery and does not block, an address that is that instance's own, a station that is public and
 * takes requests, limits on how often, and no request already waiting or declined within the last
 * 30 days. Nothing is active until a manager of the station accepts.
 */
@Singleton
public class IncomingPairRequestService {
    private static final Logger log = LoggerFactory.getLogger(IncomingPairRequestService.class);

    /** How far the moment a message was signed may lie from now, either way. */
    static final Duration MAX_DRIFT = Duration.ofMinutes(5);

    static final int MAX_NAME_LENGTH = 200;
    static final int MAX_KEY_LENGTH = 2048;
    static final int MAX_URL_LENGTH = 500;
    static final int MAX_TOKEN_LENGTH = 128;
    static final int MAX_CONTRACT_FEATURES = 64;

    private static final String REPLAY_SCOPE = "federation-pair-request";

    private final PairRequestRepository requests;
    private final FederationRepository partners;
    private final StationRepository stations;
    private final PairRequestPeers peers;
    private final PairRequestSignatures signatures;
    private final PairRequestRateLimiter rateLimiter;
    private final RemoteUrlValidator urlValidator;
    private final DatabaseReplayStore replayStore;
    private final FederationService federationService;
    private final StationSigner signer;
    private final PairRequestHttpClient httpClient;
    private final TaskScheduler scheduler;
    private final String localBaseUrl;

    @Inject
    public IncomingPairRequestService(
            PairRequestRepository requests,
            FederationRepository partners,
            StationRepository stations,
            PairRequestPeers peers,
            PairRequestSignatures signatures,
            PairRequestRateLimiter rateLimiter,
            RemoteUrlValidator urlValidator,
            DatabaseReplayStore replayStore,
            FederationService federationService,
            StationSigner signer,
            PairRequestHttpClient httpClient,
            TaskScheduler scheduler,
            Api apiConfig) {
        this.requests = requests;
        this.partners = partners;
        this.stations = stations;
        this.peers = peers;
        this.signatures = signatures;
        this.rateLimiter = rateLimiter;
        this.urlValidator = urlValidator;
        this.replayStore = replayStore;
        this.federationService = federationService;
        this.signer = signer;
        this.httpClient = httpClient;
        this.scheduler = scheduler;
        this.localBaseUrl = OutgoingPairRequestService.stripTrailingSlash(apiConfig.baseUrl());
    }

    /**
     * The requests from stations of other instances that wait for a station here to answer.
     *
     * @param stationId the asked station
     * @return the pending requests, oldest first
     */
    public List<PairRequest> pending(int stationId) {
        return requests.findPendingIncoming(stationId);
    }

    /**
     * Accepts a request from a station of another instance. This side of the partnership is written
     * at once from what the request carried; the asking instance is told in the background and
     * writes its side when the answer reaches it, or when it next asks.
     *
     * @param stationId the asked station, which must be the one the request is for
     * @param requestId the request
     * @return this side of the partnership, active
     */
    public FederationPartner accept(int stationId, int requestId) {
        var request = requirePending(stationId, requestId, FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_ACCEPT);
        var station = stations.findById(stationId).orElseThrow(FederationRefusal.FEDERATION_STATION_NOT_HERE::raise);
        if (!requests.answer(request.id(), PairRequestStatus.ACCEPTED)) {
            throw FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_ACCEPT.raise();
        }
        var partner = partners.createRemotePartner(
                stationId,
                request.remoteStationUid(),
                signer.ensurePublicKey(stationId),
                request.requireRemotePublicKey(),
                request.remoteBaseUrl(),
                request.remoteStationName(),
                Objects.requireNonNull(request.remoteContract(), "an incoming request carries its contract"));
        federationService.enableEveryCapability(partner);
        tellRequester(station, request, PairRequestStatus.ACCEPTED);
        log.info("Station {} accepted request {} from station {}", stationId, requestId, request.remoteStationUid());
        return partner;
    }

    /**
     * Declines a request from a station of another instance. The same station may not ask again for
     * 30 days; the asking instance is told in the background, or when it next asks.
     *
     * @param stationId the asked station, which must be the one the request is for
     * @param requestId the request
     */
    public void decline(int stationId, int requestId) {
        var request = requirePending(stationId, requestId, FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_DECLINE);
        var station = stations.findById(stationId).orElseThrow(FederationRefusal.FEDERATION_STATION_NOT_HERE::raise);
        if (!requests.answer(request.id(), PairRequestStatus.DECLINED)) {
            throw FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_DECLINE.raise();
        }
        tellRequester(station, request, PairRequestStatus.DECLINED);
        log.info("Station {} declined request {} from station {}", stationId, requestId, request.remoteStationUid());
    }

    /**
     * Answers the asking instance's question about where its request stands. The question must be
     * signed with the keys the request came with, so nobody else learns whether a station said yes.
     *
     * @param query the signed question
     * @return the signed answer
     */
    public PairRequestAnswer status(PairRequestStatusQuery query) {
        requireComplete(query);
        requireInTime(query.issuedAt());
        var station =
                stations.findByUid(query.targetStationUid()).orElseThrow(FederationRefusal.PAIR_STATUS_NOT_HERE::raise);
        var request = requests.find(station.id(), PairRequestDirection.INCOMING, query.requesterStationUid())
                .orElseThrow(FederationRefusal.PAIR_STATUS_NOT_HERE::raise);
        if (!signatures.holds(query, request.requireRemotePublicKey(), request.remoteInstanceKey())) {
            throw FederationRefusal.PAIR_REQUEST_STATION_SIGNATURE_NOT_GOOD.raise();
        }
        requireFirstSighting(query.nonce(), query.issuedAt());
        return answerOf(station, request, request.status());
    }

    private PairRequest requirePending(int stationId, int requestId, FederationRefusal missing) {
        return requests.findById(requestId)
                .filter(request -> request.stationId() == stationId)
                .filter(request -> request.direction() == PairRequestDirection.INCOMING)
                .filter(request -> request.status() == PairRequestStatus.PENDING)
                .orElseThrow(missing::raise);
    }

    private PairRequestAnswer answerOf(Station station, PairRequest request, PairRequestStatus status) {
        return signatures.answer(
                station.id(),
                request.remoteStationUid(),
                station.uid(),
                status,
                station.name(),
                localBaseUrl,
                FederationContractVersions.current());
    }

    /**
     * Pushes the answer to the asking instance without holding up the person who gave it. An answer
     * that does not arrive is not lost: the asking instance asks for it on its own schedule.
     */
    private void tellRequester(Station station, PairRequest request, PairRequestStatus status) {
        var answer = answerOf(station, request, status);
        scheduler.background("federation-pair-request-answer", () -> {
            if (!httpClient.deliverAnswer(request.remoteBaseUrl(), answer)) {
                log.info(
                        "The answer to request {} did not reach {}; it is handed over when that instance asks",
                        request.id(),
                        request.remoteBaseUrl());
            }
        });
    }

    private static void requireComplete(PairRequestStatusQuery query) {
        if (query.requesterStationUid() == null
                || query.targetStationUid() == null
                || query.issuedAt() == null
                || isBlank(query.nonce())
                || isBlank(query.stationSignature())
                || isBlank(query.instanceSignature())) {
            throw FederationRefusal.PAIR_REQUEST_INCOMPLETE.raise();
        }
        if (query.nonce().length() > MAX_TOKEN_LENGTH
                || query.stationSignature().length() > MAX_KEY_LENGTH
                || query.instanceSignature().length() > MAX_KEY_LENGTH) {
            throw FederationRefusal.PAIR_REQUEST_TOO_LARGE.raise();
        }
    }

    /**
     * Takes a request from a station of another instance, or refuses it with the reason.
     *
     * @param message the signed request
     * @return the asked station's name, for the asking side to show
     */
    public PairRequestReceipt receive(PairRequestMessage message) {
        requireComplete(message);
        requireInTime(message.issuedAt());
        var peer = requireKnownPeer(message.requesterInstanceKey());
        if (!signatures.instanceSignatureHolds(message)) {
            throw FederationRefusal.PAIR_REQUEST_INSTANCE_SIGNATURE_NOT_GOOD.raise();
        }
        if (!signatures.stationSignatureHolds(message)) {
            throw FederationRefusal.PAIR_REQUEST_STATION_SIGNATURE_NOT_GOOD.raise();
        }
        requireFirstSighting(message.nonce(), message.issuedAt());
        requireTheInstancesAddress(message.requesterBaseUrl(), peer);
        if (!FederationContractVersions.current()
                .core()
                .equals(message.contract().core())) {
            throw FederationRefusal.PAIR_REQUEST_CONTRACT_MISMATCH.raise();
        }
        RateLimits.enforce(
                FederationRefusal.PAIR_REQUEST_TOO_MANY_FROM_INSTANCE,
                rateLimiter.tryInstance(message.requesterInstanceKey()));

        var target = requireOpenStation(message.targetStationUid(), message.requesterStationUid());
        RateLimits.enforce(FederationRefusal.PAIR_REQUEST_TOO_MANY_FOR_STATION, rateLimiter.tryStation(target.id()));
        requireNothingStanding(target.id(), message.requesterStationUid());

        var request = requests.recordIncoming(
                target.id(),
                message.requesterStationUid(),
                message.requesterStationName().strip(),
                message.requesterBaseUrl(),
                message.requesterInstanceKey(),
                message.requesterPublicKey(),
                message.contract());
        log.info(
                "Station {} received request {} to federate from station {} on {}",
                target.id(),
                request.id(),
                message.requesterStationUid(),
                message.requesterBaseUrl());
        return new PairRequestReceipt(target.name());
    }

    /**
     * Refuses a message that misses a part or carries a part longer than any honest one would be.
     * Each text is measured before anything reads it, so an oversized value never reaches a key
     * decoder or the database.
     */
    private static void requireComplete(PairRequestMessage message) {
        if (message.requesterStationUid() == null
                || message.targetStationUid() == null
                || message.requesterStationUid().equals(message.targetStationUid())
                || message.contract() == null
                || message.contract().core() == null
                || message.issuedAt() == null
                || isBlank(message.requesterStationName())
                || isBlank(message.requesterPublicKey())
                || isBlank(message.requesterBaseUrl())
                || isBlank(message.requesterInstanceKey())
                || isBlank(message.nonce())
                || isBlank(message.stationSignature())
                || isBlank(message.instanceSignature())) {
            throw FederationRefusal.PAIR_REQUEST_INCOMPLETE.raise();
        }
        if (message.requesterStationName().length() > MAX_NAME_LENGTH
                || message.requesterStationName().chars().anyMatch(Character::isISOControl)
                || message.requesterPublicKey().length() > MAX_KEY_LENGTH
                || message.requesterBaseUrl().length() > MAX_URL_LENGTH
                || message.requesterInstanceKey().length() > MAX_TOKEN_LENGTH
                || message.nonce().length() > MAX_TOKEN_LENGTH
                || message.stationSignature().length() > MAX_KEY_LENGTH
                || message.instanceSignature().length() > MAX_KEY_LENGTH
                || exceeds(message.contract())) {
            throw FederationRefusal.PAIR_REQUEST_TOO_LARGE.raise();
        }
    }

    private static boolean exceeds(FederationContract contract) {
        return contract.core().length() > MAX_TOKEN_LENGTH
                || contract.features().size() > MAX_CONTRACT_FEATURES;
    }

    static void requireInTime(Instant issuedAt) {
        if (!SignedRequests.withinDrift(issuedAt, Instant.now(), MAX_DRIFT)) {
            throw FederationRefusal.PAIR_REQUEST_OUT_OF_TIME.raise();
        }
    }

    private void requireFirstSighting(String nonce, Instant issuedAt) {
        if (!replayStore.firstSighting(
                REPLAY_SCOPE, nonce, issuedAt.plus(MAX_DRIFT).plus(MAX_DRIFT))) {
            throw FederationRefusal.PAIR_REQUEST_OUT_OF_TIME.raise();
        }
    }

    private DiscoveryPeer requireKnownPeer(String instanceKey) {
        return switch (peers.standingOf(instanceKey)) {
            case PairRequestPeers.Standing.Known known -> known.peer();
            case PairRequestPeers.Standing.Unknown _ -> throw FederationRefusal.PAIR_REQUEST_INSTANCE_UNKNOWN.raise();
            case PairRequestPeers.Standing.Blocked _ -> throw FederationRefusal.PAIR_REQUEST_INSTANCE_BLOCKED.raise();
        };
    }

    /**
     * The address a request names is where the partnership will call the asking station later, so
     * it must be the address its instance is known by here, and one this instance calls at all.
     */
    private void requireTheInstancesAddress(String baseUrl, DiscoveryPeer peer) {
        if (!urlValidator.isAllowed(baseUrl)
                || !FederationService.addressOf(baseUrl)
                        .equalsIgnoreCase(FederationService.addressOf(peer.baseUrl()))) {
            throw FederationRefusal.PAIR_REQUEST_ADDRESS_NOT_THE_INSTANCES.raise();
        }
    }

    /**
     * The asked station, as long as it is a public station that takes requests. Anything else is
     * answered the same way as a station that does not exist.
     */
    private Station requireOpenStation(UUID targetStationUid, UUID requesterStationUid) {
        return stations.findByUid(targetStationUid)
                .filter(station -> station.discoveryVisibility() == DiscoveryVisibility.PUBLIC)
                .filter(station -> station.stationKind() == StationKind.REGULAR)
                .filter(station -> !station.uid().equals(requesterStationUid))
                .orElseThrow(FederationRefusal.PAIR_REQUEST_STATION_NOT_HERE::raise);
    }

    private void requireNothingStanding(int stationId, UUID requesterStationUid) {
        if (partners.findPartnerByStationAndRemoteUid(stationId, requesterStationUid)
                .isPresent()) {
            throw FederationRefusal.PAIR_REQUEST_ALREADY_PARTNERS.raise();
        }
        var earlier = requests.find(stationId, PairRequestDirection.INCOMING, requesterStationUid);
        if (earlier.isEmpty()) return;
        if (earlier.get().status() == PairRequestStatus.PENDING) {
            throw FederationRefusal.PAIR_REQUEST_ALREADY_WAITING.raise();
        }
        if (earlier.get().coolingDown(Instant.now())) {
            throw FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY.raise();
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
