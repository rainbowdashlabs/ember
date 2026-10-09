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
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.FederationRequestAnswered;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestReason;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.PairRequestRepository;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.service.PairRequestHttpClient.Delivery;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.WebOrigins;
import io.javalin.http.HttpStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Requests to federate that stations here send to stations of other instances.
 *
 * <p>The request is signed by the station and by this instance, sent, and written down only once the
 * other instance has taken it, so a request that never arrived is never shown as waiting. The other
 * instance's refusal reaches the person who sent it under its own name; an instance that predates
 * these requests is told apart from one that refused, because only the second can be asked again.
 *
 * <p>Where a request stands is asked in the background, never while somebody waits: when the
 * federation page opens, for the requests not asked about for a while, and on a schedule for every
 * request still waiting. The scheduled round asks the instances in parallel, each instance's requests
 * one after the other.
 */
@Singleton
public class OutgoingPairRequestService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(OutgoingPairRequestService.class);

    /** How long an answer already asked for is not asked for again when the page opens. */
    static final Duration ASK_AGAIN_AFTER = Duration.ofMinutes(1);

    /** How long a pending request goes unasked before the scheduled round asks again. */
    static final Duration SCHEDULED_ASK_AFTER = Duration.ofMinutes(10);

    private static final String REPLAY_SCOPE = "federation-pair-answer";
    private static final PairRequestGuards.StandingRefusals STANDING = new PairRequestGuards.StandingRefusals(
            DiscoveryRefusal.ALREADY_FEDERATED, DiscoveryRefusal.FEDERATION_REQUEST_ALREADY_SENT);

    private final PairRequestRepository requests;
    private final FederationRepository partners;
    private final StationRepository stations;
    private final PairRequestPeers peers;
    private final PairRequestSignatures signatures;
    private final PairRequestGuards guards;
    private final PairRequestHttpClient httpClient;
    private final StationSigner signer;
    private final RemoteUrlValidator urlValidator;
    private final FederationService federationService;
    private final TaskScheduler scheduler;
    private final DomainEventBus eventBus;
    private final String localBaseUrl;

    @Inject
    public OutgoingPairRequestService(
            PairRequestRepository requests,
            FederationRepository partners,
            StationRepository stations,
            PairRequestPeers peers,
            PairRequestSignatures signatures,
            PairRequestGuards guards,
            PairRequestHttpClient httpClient,
            StationSigner signer,
            RemoteUrlValidator urlValidator,
            FederationService federationService,
            TaskScheduler scheduler,
            DomainEventBus eventBus,
            Api apiConfig) {
        this.requests = requests;
        this.partners = partners;
        this.stations = stations;
        this.peers = peers;
        this.signatures = signatures;
        this.guards = guards;
        this.httpClient = httpClient;
        this.signer = signer;
        this.urlValidator = urlValidator;
        this.federationService = federationService;
        this.scheduler = scheduler;
        this.eventBus = eventBus;
        this.localBaseUrl = WebOrigins.stripTrailingSlash(apiConfig.baseUrl());
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

    /**
     * Asks about every due request: every instance at once, each instance's requests one after the
     * other, and each asking station's identity read once.
     */
    private void askAllDue() {
        var due = requests.findPendingOutgoingDue(Instant.now().minus(SCHEDULED_ASK_AFTER));
        Map<Integer, UUID> stationUids = new HashMap<>();
        for (var request : due) {
            stationUids.computeIfAbsent(request.stationId(), stations::requireUid);
        }
        var byInstance = due.stream().collect(Collectors.groupingBy(PairRequest::remoteBaseUrl));
        var rounds = byInstance.values().stream()
                .map(batch -> CompletableFuture.runAsync(
                        () -> batch.forEach(request -> ask(request, stationUids.get(request.stationId()))),
                        scheduler.executor()))
                .toList();
        for (var round : rounds) {
            round.exceptionally(failure -> {
                        log.warn("Asking about requests to federate failed", failure);
                        return null;
                    })
                    .join();
        }
    }

    /**
     * What a station here asked of other instances: everything still waiting, and what was answered
     * within the last 30 days, as stored. Requests not asked about for a while are handed to the
     * background to be asked about, so an answer that arrived over there while its push did not
     * reach here shows the next time the page is read.
     *
     * @param stationId the asking station
     * @return the requests, newest first
     */
    public List<PairRequest> outgoing(int stationId) {
        var listed = requests.findOutgoing(stationId, Instant.now().minus(PairRequest.DECLINE_COOLDOWN));
        var askedBefore = Instant.now().minus(ASK_AGAIN_AFTER);
        var due = listed.stream().filter(request -> isDue(request, askedBefore)).toList();
        if (!due.isEmpty()) {
            UUID stationUid = stations.requireUid(stationId);
            scheduler.background(
                    "federation-pair-request-page-status", () -> due.forEach(request -> ask(request, stationUid)));
        }
        return listed;
    }

    private static boolean isDue(PairRequest request, Instant askedBefore) {
        Instant checked = request.checkedAt();
        return request.status() == PairRequestStatus.PENDING && (checked == null || checked.isBefore(askedBefore));
    }

    /**
     * The requests a station here sent that still wait for their answer.
     *
     * @param stationId the asking station
     * @return the waiting requests
     */
    public List<PairRequest> waiting(int stationId) {
        return requests.findOutgoing(stationId, Instant.now()).stream()
                .filter(request -> request.status() == PairRequestStatus.PENDING)
                .toList();
    }

    /**
     * Takes the answer the asked instance pushed. It has to be signed by the instance the request
     * was sent to, and for an acceptance by the station key it hands over.
     *
     * @param answer the signed answer
     */
    public void receiveAnswer(PairRequestAnswer answer) {
        PairRequestGuards.requireComplete(answer);
        PairRequestGuards.requireInTime(answer.issuedAt());
        var request = stations.findHereByUid(answer.requesterStationUid())
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
    private void ask(PairRequest request, UUID stationUid) {
        requests.markChecked(request.id());
        var query = signatures.query(request.stationId(), stationUid, request.remoteStationUid());
        var answer = httpClient
                .askStatus(request.remoteBaseUrl(), query)
                .filter(found -> stationUid.equals(found.requesterStationUid())
                        && request.remoteStationUid().equals(found.targetStationUid()));
        if (answer.isEmpty()) return;
        try {
            PairRequestGuards.requireComplete(answer.get());
            PairRequestGuards.requireInTime(answer.get().issuedAt());
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
        guards.requireFirstSighting(REPLAY_SCOPE, answer.nonce(), answer.issuedAt());
        switch (answer.status()) {
            case PENDING -> {}
            case DECLINED -> {
                requests.answer(request.id(), PairRequestStatus.DECLINED);
                log.info("Station {} learned that request {} was declined", request.stationId(), request.id());
                eventBus.publish(
                        new FederationRequestAnswered(request.stationId(), request.remoteStationName(), false));
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
        eventBus.publish(new FederationRequestAnswered(
                request.stationId(), answer.stationName().strip(), true));
        log.info("Station {} is now federated with station {}", request.stationId(), request.remoteStationUid());
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
        guards.requireNothingStanding(stationId, PairRequestDirection.OUTGOING, target.stationUid(), STANDING);
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

    /**
     * The refusal the asking person is shown for a request the other instance did not take: the one
     * its reason stands for where it named one, and otherwise what the failure says about the other
     * instance. A route it does not have is an instance that predates these requests.
     */
    static Refusal refusalFor(Delivery delivery) {
        return switch (delivery) {
            case Delivery.Taken _ -> throw new IllegalStateException("A request that was taken is not a refusal");
            case Delivery.Failed failed ->
                switch (failed.failure()) {
                    case ADDRESS_REFUSED -> FederationRefusal.PAIR_REQUEST_PEER_ADDRESS_REFUSED;
                    case TIMED_OUT, UNREACHABLE -> FederationRefusal.PAIR_REQUEST_PEER_UNREACHABLE;
                };
            case Delivery.Answered answered -> refusalFor(answered);
        };
    }

    private static Refusal refusalFor(Delivery.Answered answered) {
        PairRequestReason reason = answered.reason();
        if (reason != null) return reason.refusal();
        boolean noSuchRoute = answered.status() == HttpStatus.NOT_FOUND.getCode()
                || answered.status() == HttpStatus.METHOD_NOT_ALLOWED.getCode();
        return noSuchRoute
                ? FederationRefusal.PAIR_REQUEST_PEER_TOO_OLD
                : FederationRefusal.PAIR_REQUEST_REFUSED_BY_PEER;
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
