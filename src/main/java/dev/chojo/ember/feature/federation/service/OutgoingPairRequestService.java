/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.PairRequestRepository;
import dev.chojo.ember.feature.federation.route.RemotePairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.service.PairRequestHttpClient.Delivery;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import io.javalin.http.HttpStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
public class OutgoingPairRequestService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(OutgoingPairRequestService.class);

    /** How long an answer already asked for is not asked for again when the page opens. */
    static final Duration ASK_AGAIN_AFTER = Duration.ofMinutes(1);

    /** How long a pending request goes unasked before the scheduled round asks again. */
    static final Duration SCHEDULED_ASK_AFTER = Duration.ofMinutes(10);

    private static final String REPLAY_SCOPE = "federation-pair-answer";

    private final PairRequestRepository requests;
    private final FederationRepository partners;
    private final StationRepository stations;
    private final PairRequestPeers peers;
    private final PairRequestSignatures signatures;
    private final PairRequestHttpClient httpClient;
    private final StationSigner signer;
    private final RemoteUrlValidator urlValidator;
    private final FederationService federationService;
    private final DatabaseReplayStore replayStore;
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
            FederationService federationService,
            DatabaseReplayStore replayStore,
            Api apiConfig) {
        this.requests = requests;
        this.partners = partners;
        this.stations = stations;
        this.peers = peers;
        this.signatures = signatures;
        this.httpClient = httpClient;
        this.signer = signer;
        this.urlValidator = urlValidator;
        this.federationService = federationService;
        this.replayStore = replayStore;
        this.localBaseUrl = stripTrailingSlash(apiConfig.baseUrl());
    }

    /**
     * Asks the other instances about every request still waiting for an answer, so the asking
     * station hears of an answer whose push never arrived even when nobody opens the page.
     */
    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "federation-pair-request-status",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(15)),
                this::askAllDue));
    }

    private void askAllDue() {
        for (var request : requests.findPendingOutgoingDue(Instant.now().minus(SCHEDULED_ASK_AFTER))) {
            ask(request);
        }
    }

    /**
     * What a station here asked of other instances: everything still waiting, and what was answered
     * within the last 30 days. Requests not asked about for a while are asked about first, so the
     * page shows an answer that has arrived over there even when its push did not reach here.
     *
     * @param stationId the asking station
     * @return the requests, newest first
     */
    public List<PairRequest> outgoing(int stationId) {
        var answeredSince = Instant.now().minus(PairRequest.DECLINE_COOLDOWN);
        var due = Instant.now().minus(ASK_AGAIN_AFTER);
        for (var request : requests.findOutgoing(stationId, answeredSince)) {
            var checked = request.checkedAt();
            if (request.status() == PairRequestStatus.PENDING && (checked == null || checked.isBefore(due))) {
                ask(request);
            }
        }
        return requests.findOutgoing(stationId, answeredSince);
    }

    /**
     * Takes the answer the asked instance pushed. It has to be signed by the instance the request
     * was sent to, and for an acceptance by the station key it hands over.
     *
     * @param answer the signed answer
     */
    public void receiveAnswer(PairRequestAnswer answer) {
        requireComplete(answer);
        IncomingPairRequestService.requireInTime(answer.issuedAt());
        var request = stations.findByUid(answer.requesterStationUid())
                .flatMap(station ->
                        requests.find(station.id(), PairRequestDirection.OUTGOING, answer.targetStationUid()))
                .filter(found -> found.status() == PairRequestStatus.PENDING)
                .orElseThrow(FederationRefusal.PAIR_ANSWER_NOT_EXPECTED::raise);
        apply(request, answer);
    }

    /**
     * Asks the instance a request went to where it stands, and takes the answer as if it had been
     * pushed. Nothing that goes wrong here is the asking person's doing, so it is logged and the
     * request stays as it was.
     */
    private void ask(PairRequest request) {
        requests.markChecked(request.id());
        UUID stationUid = stations.requireUid(request.stationId());
        var query = signatures.query(request.stationId(), stationUid, request.remoteStationUid());
        var answer = httpClient
                .askStatus(request.remoteBaseUrl(), query)
                .filter(found -> stationUid.equals(found.requesterStationUid())
                        && request.remoteStationUid().equals(found.targetStationUid()));
        if (answer.isEmpty()) return;
        try {
            requireComplete(answer.get());
            IncomingPairRequestService.requireInTime(answer.get().issuedAt());
            apply(request, answer.get());
        } catch (RefusalResponse refused) {
            log.warn(
                    "The answer {} gave about request {} was not taken: {}",
                    request.remoteBaseUrl(),
                    request.id(),
                    refused.getMessage());
        }
    }

    private void apply(PairRequest request, PairRequestAnswer answer) {
        if (!signatures.holds(answer, request.remoteInstanceKey())) {
            throw FederationRefusal.PAIR_ANSWER_SIGNATURE_NOT_GOOD.raise();
        }
        if (!replayStore.firstSighting(
                REPLAY_SCOPE,
                answer.nonce(),
                answer.issuedAt()
                        .plus(IncomingPairRequestService.MAX_DRIFT)
                        .plus(IncomingPairRequestService.MAX_DRIFT))) {
            throw FederationRefusal.PAIR_REQUEST_OUT_OF_TIME.raise();
        }
        switch (answer.status()) {
            case PENDING -> {}
            case DECLINED -> {
                requests.answer(request.id(), PairRequestStatus.DECLINED);
                log.info("Station {} learned that request {} was declined", request.stationId(), request.id());
            }
            case ACCEPTED -> establish(request, answer);
        }
    }

    /**
     * Writes this side of the partnership the asked station agreed to. The address stays the one
     * the request was sent to: the answer was signed by that instance, and an answer pointing the
     * partnership somewhere else is not one this side asked for.
     */
    private void establish(PairRequest request, PairRequestAnswer answer) {
        var partner = partners.createRemotePartner(
                request.stationId(),
                request.remoteStationUid(),
                signer.ensurePublicKey(request.stationId()),
                Objects.requireNonNull(answer.publicKey(), "a verified acceptance carries its key"),
                request.remoteBaseUrl(),
                answer.stationName().strip(),
                Objects.requireNonNull(answer.contract(), "an acceptance carries its contract"));
        federationService.enableEveryCapability(partner);
        requests.delete(request.id());
        log.info("Station {} is now federated with station {}", request.stationId(), request.remoteStationUid());
    }

    private static void requireComplete(PairRequestAnswer answer) {
        boolean accepted = answer.status() == PairRequestStatus.ACCEPTED;
        if (answer.requesterStationUid() == null
                || answer.targetStationUid() == null
                || answer.status() == null
                || answer.issuedAt() == null
                || isBlank(answer.stationName())
                || isBlank(answer.baseUrl())
                || isBlank(answer.nonce())
                || isBlank(answer.instanceSignature())
                || (accepted && (isBlank(answer.publicKey()) || answer.contract() == null))) {
            throw FederationRefusal.PAIR_REQUEST_INCOMPLETE.raise();
        }
        if (answer.stationName().length() > IncomingPairRequestService.MAX_NAME_LENGTH
                || answer.baseUrl().length() > IncomingPairRequestService.MAX_URL_LENGTH
                || answer.nonce().length() > IncomingPairRequestService.MAX_TOKEN_LENGTH
                || answer.instanceSignature().length() > IncomingPairRequestService.MAX_KEY_LENGTH
                || lengthOf(answer.publicKey()) > IncomingPairRequestService.MAX_KEY_LENGTH
                || lengthOf(answer.stationSignature()) > IncomingPairRequestService.MAX_KEY_LENGTH) {
            throw FederationRefusal.PAIR_REQUEST_TOO_LARGE.raise();
        }
    }

    private static int lengthOf(@Nullable String value) {
        return value == null ? 0 : value.length();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
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

    static String stripTrailingSlash(String url) {
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
