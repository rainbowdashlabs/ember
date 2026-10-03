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
import dev.chojo.ember.auth.signing.repository.SignedRequestNonceRepository;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.FederationRequestAnswered;
import dev.chojo.ember.event.events.FederationRequestReceived;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import dev.chojo.ember.feature.discovery.repository.DiscoveryBlocklistRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.discovery.service.DiscoverySigningService;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.entity.PairRequestDirection;
import dev.chojo.ember.feature.federation.entity.PairRequestStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.PairRequestRepository;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestAnswer;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestMessage;
import dev.chojo.ember.feature.federation.route.PairRequestRoutes.PairRequestStatusQuery;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService.RemoteTarget;
import dev.chojo.ember.feature.federation.service.PairRequestHttpClient.Delivery;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Requests to federate between stations of two instances.
 *
 * <p>Both instances run in this test, the way {@link FederationEnrollmentServiceTest} runs them: one
 * database, which is what a test can offer, and otherwise everything twice. Each side has its own
 * base URL, its own discovery key and its own services, and the only way one reaches the other is the
 * stubbed HTTP client, which hands a request to the other side and turns its refusal back into the
 * status and code the wire would carry.
 */
class PairRequestExchangeTest extends RepositoryTestBase {
    private static final String URL_HERE = "https://93.184.216.34";
    private static final String URL_THERE = "https://104.16.5.7:8443";

    @TempDir
    Path keysHere;

    @TempDir
    Path keysThere;

    @TempDir
    Path keysStranger;

    private final PairRequestRepository requests = new PairRequestRepository();
    private final FederationRepository partners = new FederationRepository();
    private final DiscoveryPeerRepository peerRepo = new DiscoveryPeerRepository();
    private final StationKeyStore stationKeys = TestStationKeys.store();
    private final FederationSigningService federationSigning = new FederationSigningService();
    private final PairRequestHttpClient httpClient = mock(PairRequestHttpClient.class);
    private final TaskScheduler scheduler = inline();
    private final DomainEventBus events = mock(DomainEventBus.class);
    private boolean pushReaches = true;

    private Side here;
    private Side there;
    private Station asking;
    private Station asked;
    private PairRequestRateLimiter limiter;

    /** One instance: its key, its signatures and the two services of a request. */
    private record Side(
            String baseUrl,
            DiscoverySigningService discovery,
            PairRequestSignatures signatures,
            IncomingPairRequestService incoming,
            OutgoingPairRequestService outgoing) {
        String instanceKey() {
            return discovery.publicKeyBase64();
        }
    }

    private static Api api(String baseUrl) {
        var api = new Api();
        try {
            var field = Api.class.getDeclaredField("baseUrl");
            field.setAccessible(true);
            field.set(api, baseUrl);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return api;
    }

    private Side side(String baseUrl, Path keyDir, PairRequestRateLimiter rateLimiter) {
        var discovery = new DiscoverySigningService(new DiscoveryKeyService(keyDir));
        var signer = new StationSigner(stationKeys, federationSigning);
        var signatures = new PairRequestSignatures(signer, federationSigning, discovery);
        var urls = new RemoteUrlValidator(new Federation(), new Demo());
        var peers = new PairRequestPeers(peerRepo, new DiscoveryBlocklistRepository());
        var federation = new FederationService(partners, stationRepo, stationKeys, api(baseUrl));
        var replayStore = new DatabaseReplayStore(new SignedRequestNonceRepository());
        var incoming = new IncomingPairRequestService(
                requests,
                partners,
                stationRepo,
                peers,
                signatures,
                rateLimiter,
                urls,
                replayStore,
                federation,
                signer,
                httpClient,
                scheduler,
                events,
                api(baseUrl));
        var outgoing = new OutgoingPairRequestService(
                requests,
                partners,
                stationRepo,
                peers,
                signatures,
                httpClient,
                signer,
                urls,
                federation,
                replayStore,
                events,
                api(baseUrl));
        return new Side(baseUrl, discovery, signatures, incoming, outgoing);
    }

    /** Runs the work handed to the background at once, so a test sees what it did. */
    private static TaskScheduler inline() {
        var scheduler = mock(TaskScheduler.class);
        when(scheduler.background(anyString(), any())).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return true;
        });
        return scheduler;
    }

    @BeforeEach
    void setup() {
        limiter = new PairRequestRateLimiter(Clock.systemUTC());
        here = side(URL_HERE, keysHere, limiter);
        there = side(URL_THERE, keysThere, limiter);
        peerRepo.upsert(here.instanceKey(), URL_HERE, "here", PeerSource.MANUAL, null);
        peerRepo.upsert(there.instanceKey(), URL_THERE, "there", PeerSource.MANUAL, null);
        asking = stationRepo.create("Fragende Wache " + UUID.randomUUID(), DiscoveryVisibility.PUBLIC);
        asked = stationRepo.create("Gefragte Wache " + UUID.randomUUID(), DiscoveryVisibility.PUBLIC);
        stationKeys.ensurePublicKey(asking.id());
        stationKeys.ensurePublicKey(asked.id());
        letThemAnswer();
    }

    @AfterEach
    void cleanup() {
        for (var station : List.of(asking, asked)) {
            for (var partner : partners.findPartners(station.id())) {
                partners.deletePartner(partner.id());
            }
            stationRepo.delete(station.id());
        }
        peerRepo.delete(here.instanceKey());
        peerRepo.delete(there.instanceKey());
    }

    /**
     * Hands every message for the other instance to the other instance, refusals and all: the
     * request and the question about it to the asked side, the pushed answer to the asking side.
     */
    private void letThemAnswer() {
        when(httpClient.send(eq(URL_THERE), any()))
                .thenAnswer(invocation -> overTheWire(() -> new Delivery.Taken(there.incoming()
                        .receive(invocation.getArgument(1, PairRequestMessage.class))
                        .stationName())));
        when(httpClient.askStatus(eq(URL_THERE), any())).thenAnswer(invocation -> {
            try {
                return Optional.of(there.incoming().status(invocation.getArgument(1, PairRequestStatusQuery.class)));
            } catch (RefusalResponse refused) {
                return Optional.empty();
            }
        });
        when(httpClient.deliverAnswer(eq(URL_HERE), any())).thenAnswer(invocation -> {
            if (!pushReaches) return false;
            try {
                here.outgoing().receiveAnswer(invocation.getArgument(1, PairRequestAnswer.class));
                return true;
            } catch (RefusalResponse refused) {
                return false;
            }
        });
    }

    static Delivery overTheWire(Supplier<Delivery> call) {
        try {
            return call.get();
        } catch (RefusalResponse refused) {
            return new Delivery.Answered(refused.getStatus(), refused.refusal().code(), refused.getMessage());
        }
    }

    private RemoteTarget theAskedStation() {
        return new RemoteTarget(asked.uid(), URL_THERE, there.instanceKey());
    }

    private PairRequest send() {
        return here.outgoing().send(asking.id(), theAskedStation());
    }

    private PairRequestMessage freshRequest() {
        return freshRequest(asking.name());
    }

    private PairRequestMessage freshRequest(String name) {
        return here.signatures()
                .request(
                        asking.id(),
                        asking.uid(),
                        name,
                        stationKeys.ensurePublicKey(asking.id()),
                        URL_HERE,
                        asked.uid(),
                        FederationContractVersions.current());
    }

    /** A request as the asking instance would send it, with the given parts and both signatures over them. */
    private PairRequestMessage signed(PairRequestMessage unsigned, DiscoverySigningService instance) {
        var withKey = new PairRequestMessage(
                unsigned.requesterStationUid(),
                unsigned.requesterStationName(),
                unsigned.requesterPublicKey(),
                unsigned.requesterBaseUrl(),
                instance.publicKeyBase64(),
                unsigned.targetStationUid(),
                unsigned.contract(),
                unsigned.issuedAt(),
                unsigned.nonce(),
                "",
                "");
        String payload = PairRequestSignatures.payloadOf(withKey);
        return new PairRequestMessage(
                withKey.requesterStationUid(),
                withKey.requesterStationName(),
                withKey.requesterPublicKey(),
                withKey.requesterBaseUrl(),
                withKey.requesterInstanceKey(),
                withKey.targetStationUid(),
                withKey.contract(),
                withKey.issuedAt(),
                withKey.nonce(),
                new StationSigner(stationKeys, federationSigning).signEnrollment(asking.id(), payload),
                instance.sign(payload));
    }

    private PairRequestMessage withParts(String baseUrl, FederationContract contract, Instant issuedAt) {
        var fresh = freshRequest();
        return signed(
                new PairRequestMessage(
                        fresh.requesterStationUid(),
                        fresh.requesterStationName(),
                        fresh.requesterPublicKey(),
                        baseUrl,
                        fresh.requesterInstanceKey(),
                        fresh.targetStationUid(),
                        contract,
                        issuedAt,
                        UUID.randomUUID().toString(),
                        "",
                        ""),
                here.discovery());
    }

    private static Refusal refusalOf(Executable call) {
        return assertThrows(RefusalResponse.class, call).refusal();
    }

    private Refusal refusedThere(PairRequestMessage message) {
        return refusalOf(() -> there.incoming().receive(message));
    }

    @Test
    void aRequestIsTakenAndWaitsOnBothSides() {
        var sent = send();

        assertEquals(PairRequestStatus.PENDING, sent.status());
        assertEquals(asked.name(), sent.remoteStationName());
        assertEquals(URL_THERE, sent.remoteBaseUrl());
        assertEquals(there.instanceKey(), sent.remoteInstanceKey());

        var received = requests.find(asked.id(), PairRequestDirection.INCOMING, asking.uid())
                .orElseThrow();
        assertEquals(PairRequestStatus.PENDING, received.status());
        assertEquals(asking.name(), received.remoteStationName());
        assertEquals(URL_HERE, received.remoteBaseUrl());
        assertEquals(here.instanceKey(), received.remoteInstanceKey());
        assertEquals(stationKeys.ensurePublicKey(asking.id()), received.remotePublicKey());
    }

    @Test
    void aKeySwappedInAfterSigningIsRefused() {
        var fresh = freshRequest();
        var forged = new PairRequestMessage(
                fresh.requesterStationUid(),
                fresh.requesterStationName(),
                stationKeys.ensurePublicKey(asked.id()),
                fresh.requesterBaseUrl(),
                fresh.requesterInstanceKey(),
                fresh.targetStationUid(),
                fresh.contract(),
                fresh.issuedAt(),
                fresh.nonce(),
                fresh.stationSignature(),
                fresh.instanceSignature());

        assertEquals(FederationRefusal.PAIR_REQUEST_INSTANCE_SIGNATURE_NOT_GOOD, refusedThere(forged));
    }

    @Test
    void aStationKeyTheSignatureDoesNotFitIsRefusedEvenWithAGoodInstanceSignature() {
        var fresh = freshRequest();
        var unsigned = new PairRequestMessage(
                fresh.requesterStationUid(),
                fresh.requesterStationName(),
                stationKeys.ensurePublicKey(asked.id()),
                fresh.requesterBaseUrl(),
                fresh.requesterInstanceKey(),
                fresh.targetStationUid(),
                fresh.contract(),
                fresh.issuedAt(),
                fresh.nonce(),
                "",
                "");

        assertEquals(
                FederationRefusal.PAIR_REQUEST_STATION_SIGNATURE_NOT_GOOD,
                refusedThere(signed(unsigned, here.discovery())));
    }

    @Test
    void anInstanceSignatureThatDoesNotFitIsRefused() {
        var fresh = freshRequest();
        var forged = new PairRequestMessage(
                fresh.requesterStationUid(),
                fresh.requesterStationName() + " und mehr",
                fresh.requesterPublicKey(),
                fresh.requesterBaseUrl(),
                fresh.requesterInstanceKey(),
                fresh.targetStationUid(),
                fresh.contract(),
                fresh.issuedAt(),
                fresh.nonce(),
                fresh.stationSignature(),
                fresh.instanceSignature());

        assertEquals(FederationRefusal.PAIR_REQUEST_INSTANCE_SIGNATURE_NOT_GOOD, refusedThere(forged));
    }

    @Test
    void anInstanceNeverMetIsRefused() {
        var stranger = new DiscoverySigningService(new DiscoveryKeyService(keysStranger));

        assertEquals(FederationRefusal.PAIR_REQUEST_INSTANCE_UNKNOWN, refusedThere(signed(freshRequest(), stranger)));
    }

    @Test
    void aBlockedInstanceIsRefusedAndTheAskingPersonIsToldSo() {
        peerRepo.setBlocked(here.instanceKey(), true);

        assertEquals(FederationRefusal.PAIR_REQUEST_INSTANCE_BLOCKED, refusalOf(this::send));
        assertTrue(requests.find(asking.id(), PairRequestDirection.OUTGOING, asked.uid())
                .isEmpty());
    }

    @Test
    void aStationThatIsNotPublicAnswersLikeOneThatDoesNotExist() {
        stationRepo.updateDiscoverySettings(asked.id(), DiscoveryVisibility.INSTANCE, "", false);

        assertEquals(FederationRefusal.PAIR_REQUEST_STATION_NOT_HERE, refusalOf(this::send));
    }

    @Test
    void aSecondRequestWaitsForTheFirst() {
        send();

        assertEquals(DiscoveryRefusal.FEDERATION_REQUEST_ALREADY_SENT, refusalOf(this::send));
        assertEquals(FederationRefusal.PAIR_REQUEST_ALREADY_WAITING, refusedThere(freshRequest()));
    }

    @Test
    void aDeclineKeepsTheStationFromAskingAgainForThirtyDays() {
        send();
        var received = requests.find(asked.id(), PairRequestDirection.INCOMING, asking.uid())
                .orElseThrow();
        requests.answer(received.id(), PairRequestStatus.DECLINED);

        assertEquals(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY, refusedThere(freshRequest()));
    }

    @Test
    void stationsThatArePartnersAlreadyAreNotAskedAgain() {
        partners.createRemotePartner(
                asked.id(),
                asking.uid(),
                stationKeys.ensurePublicKey(asked.id()),
                stationKeys.ensurePublicKey(asking.id()),
                URL_HERE,
                asking.name(),
                FederationContractVersions.current());

        assertEquals(FederationRefusal.PAIR_REQUEST_ALREADY_PARTNERS, refusedThere(freshRequest()));
    }

    @Test
    void tooManyRequestsFromOneInstanceAreRefused() {
        var busy = mock(PairRequestRateLimiter.class);
        when(busy.tryInstance(anyString())).thenReturn(Optional.of(60L));
        there = side(URL_THERE, keysThere, busy);

        assertEquals(FederationRefusal.PAIR_REQUEST_TOO_MANY_FROM_INSTANCE, refusedThere(freshRequest()));
    }

    @Test
    void tooManyRequestsForOneStationAreRefused() {
        var busy = mock(PairRequestRateLimiter.class);
        when(busy.tryInstance(anyString())).thenReturn(Optional.empty());
        when(busy.tryStation(asked.id())).thenReturn(Optional.of(60L));
        there = side(URL_THERE, keysThere, busy);

        assertEquals(FederationRefusal.PAIR_REQUEST_TOO_MANY_FOR_STATION, refusedThere(freshRequest()));
    }

    @Test
    void anOversizedNameIsRefusedBeforeAnythingReadsIt() {
        assertEquals(
                FederationRefusal.PAIR_REQUEST_TOO_LARGE,
                refusedThere(freshRequest("W".repeat(IncomingPairRequestService.MAX_NAME_LENGTH + 1))));
    }

    @Test
    void aRequestWithoutItsPartsIsRefused() {
        var fresh = freshRequest();
        var incomplete = new PairRequestMessage(
                fresh.requesterStationUid(),
                fresh.requesterStationName(),
                fresh.requesterPublicKey(),
                fresh.requesterBaseUrl(),
                fresh.requesterInstanceKey(),
                fresh.targetStationUid(),
                fresh.contract(),
                fresh.issuedAt(),
                fresh.nonce(),
                "",
                fresh.instanceSignature());

        assertEquals(FederationRefusal.PAIR_REQUEST_INCOMPLETE, refusedThere(incomplete));
    }

    @Test
    void anOldRequestAndOneSentTwiceAreRefused() {
        assertEquals(
                FederationRefusal.PAIR_REQUEST_OUT_OF_TIME,
                refusedThere(withParts(
                        URL_HERE,
                        FederationContractVersions.current(),
                        Instant.now().minus(Duration.ofMinutes(10)))));

        var request = freshRequest();
        there.incoming().receive(request);
        assertEquals(FederationRefusal.PAIR_REQUEST_OUT_OF_TIME, refusedThere(request));
    }

    @Test
    void anAddressOtherThanTheInstancesOwnIsRefused() {
        assertEquals(
                FederationRefusal.PAIR_REQUEST_ADDRESS_NOT_THE_INSTANCES,
                refusedThere(withParts("https://104.16.9.9", FederationContractVersions.current(), Instant.now())));
    }

    @Test
    void instancesWhoseContractsCannotTalkAreRefused() {
        assertEquals(
                FederationRefusal.PAIR_REQUEST_CONTRACT_MISMATCH,
                refusedThere(withParts(URL_HERE, new FederationContract("other", Map.of()), Instant.now())));
    }

    @Test
    void anInstanceWithoutTheEndpointIsAnOlderOne() {
        when(httpClient.send(eq(URL_THERE), any())).thenReturn(new Delivery.Answered(404, null, "Not found"));

        assertEquals(FederationRefusal.PAIR_REQUEST_PEER_TOO_OLD, refusalOf(this::send));
    }

    @Test
    void anInstanceThatDoesNotAnswerIsNamedSo() {
        when(httpClient.send(eq(URL_THERE), any()))
                .thenReturn(new Delivery.Failed(PairRequestHttpClient.Failure.UNREACHABLE));

        assertEquals(FederationRefusal.PAIR_REQUEST_PEER_UNREACHABLE, refusalOf(this::send));
    }

    @Test
    void aRefusalTheOtherInstanceDoesNotNameIsItsOwn() {
        assertEquals(
                FederationRefusal.PAIR_REQUEST_REFUSED_BY_PEER,
                OutgoingPairRequestService.refusalFor(new Delivery.Answered(500, "G-001", "")));
        assertEquals(
                FederationRefusal.PAIR_REQUEST_PEER_ADDRESS_REFUSED,
                OutgoingPairRequestService.refusalFor(
                        new Delivery.Failed(PairRequestHttpClient.Failure.ADDRESS_REFUSED)));
    }

    @Test
    void aStationDoesNotAskItself() {
        assertEquals(FederationRefusal.PAIR_REQUEST_TO_OWN_STATION, refusalOf(() -> here.outgoing()
                .send(asking.id(), new RemoteTarget(asking.uid(), URL_THERE, there.instanceKey()))));
    }

    @Test
    void aPairingCodeOfAKnownInstanceSendsTheSameRequest() {
        var sent = here.outgoing().sendToCode(asking.id(), asked.uid(), FederationService.addressOf(URL_THERE));

        assertEquals(PairRequestStatus.PENDING, sent.status());
        assertEquals(there.instanceKey(), sent.remoteInstanceKey());
    }

    @Test
    void aPairingCodeOfAnUnknownInstanceIsRefused() {
        assertEquals(FederationRefusal.PAIR_REQUEST_INSTANCE_NOT_KNOWN_HERE, refusalOf(() -> here.outgoing()
                .sendToCode(asking.id(), asked.uid(), "104.16.9.9")));
    }

    private PairRequest receivedThere() {
        return requests.find(asked.id(), PairRequestDirection.INCOMING, asking.uid())
                .orElseThrow();
    }

    private Optional<PairRequest> sentHere() {
        return requests.find(asking.id(), PairRequestDirection.OUTGOING, asked.uid());
    }

    private FederationPartner partnerOf(Station station, UUID partnerUid) {
        return partners.findPartnerByStationAndRemoteUid(station.id(), partnerUid)
                .orElseThrow();
    }

    private void assertBothSidesActive() {
        var ours = partnerOf(asking, asked.uid());
        var theirs = partnerOf(asked, asking.uid());
        assertEquals(FederationPartner.FederationStatus.ACTIVE, ours.status());
        assertEquals(FederationPartner.FederationStatus.ACTIVE, theirs.status());
        assertEquals(URL_THERE, ours.remoteHost());
        assertEquals(URL_HERE, theirs.remoteHost());
        assertEquals(stationKeys.ensurePublicKey(asked.id()), ours.partnerPublicKey());
        assertEquals(stationKeys.ensurePublicKey(asking.id()), theirs.partnerPublicKey());
        assertEquals(asked.name(), ours.partnerStationName());
        assertEquals(asking.name(), theirs.partnerStationName());
    }

    @Test
    void anAcceptedRequestMakesBothStationsPartners() {
        send();

        var partner = there.incoming().accept(asked.id(), receivedThere().id());

        assertEquals(asking.uid(), partner.partnerStationId());
        assertBothSidesActive();
        assertTrue(sentHere().isEmpty(), "the request turned into the partnership");
        assertEquals(PairRequestStatus.ACCEPTED, receivedThere().status());
        verify(events).publish(new FederationRequestReceived(asked.id(), asking.name()));
        verify(events).publish(new FederationRequestAnswered(asking.id(), asked.name(), true));
    }

    @Test
    void aDeclineReachesTheAskingStationAndKeepsItFromAskingAgain() {
        send();

        there.incoming().decline(asked.id(), receivedThere().id());

        assertEquals(PairRequestStatus.DECLINED, sentHere().orElseThrow().status());
        assertEquals(PairRequestStatus.DECLINED, receivedThere().status());
        assertTrue(partners.findPartnerByStationAndRemoteUid(asking.id(), asked.uid())
                .isEmpty());
        assertEquals(FederationRefusal.PAIR_REQUEST_DECLINED_RECENTLY, refusalOf(this::send));
        verify(events).publish(new FederationRequestAnswered(asking.id(), asked.name(), false));
        assertEquals(
                PairRequestStatus.DECLINED,
                here.outgoing().outgoing(asking.id()).getFirst().status());
    }

    @Test
    void anAcceptanceThatNeverArrivedIsFetchedWhenThePageOpens() {
        pushReaches = false;
        send();
        there.incoming().accept(asked.id(), receivedThere().id());
        assertEquals(PairRequestStatus.PENDING, sentHere().orElseThrow().status());

        assertTrue(here.outgoing().outgoing(asking.id()).isEmpty());
        assertBothSidesActive();
    }

    @Test
    void aDeclineThatNeverArrivedIsFetchedOnTheScheduledRound() {
        pushReaches = false;
        send();
        there.incoming().decline(asked.id(), receivedThere().id());

        var task = here.outgoing().scheduledTasks().getFirst();
        assertEquals("federation-pair-request-status", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(15)), task.schedule());
        task.work().run();

        assertEquals(PairRequestStatus.DECLINED, sentHere().orElseThrow().status());
    }

    @Test
    void aPendingRequestStaysPendingWhenAskedAbout() {
        send();

        var listed = here.outgoing().outgoing(asking.id());

        assertEquals(1, listed.size());
        assertEquals(PairRequestStatus.PENDING, listed.getFirst().status());
        assertTrue(sentHere().orElseThrow().checkedAt() != null);
    }

    @Test
    void onlyTheAskedStationAnswersARequest() {
        send();
        int requestId = receivedThere().id();

        assertEquals(FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_ACCEPT, refusalOf(() -> there.incoming()
                .accept(asking.id(), requestId)));
        assertEquals(FederationRefusal.PAIR_REQUEST_NOT_HERE_TO_DECLINE, refusalOf(() -> there.incoming()
                .decline(asking.id(), requestId)));
    }

    @Test
    void anAnswerFromAnotherInstanceIsRefused() {
        send();
        var stranger = new PairRequestSignatures(
                new StationSigner(stationKeys, federationSigning),
                federationSigning,
                new DiscoverySigningService(new DiscoveryKeyService(keysStranger)));
        var forged = stranger.answer(
                asked.id(),
                asking.uid(),
                asked.uid(),
                PairRequestStatus.ACCEPTED,
                asked.name(),
                URL_THERE,
                FederationContractVersions.current());

        assertEquals(FederationRefusal.PAIR_ANSWER_SIGNATURE_NOT_GOOD, refusalOf(() -> here.outgoing()
                .receiveAnswer(forged)));
        assertEquals(PairRequestStatus.PENDING, sentHere().orElseThrow().status());
    }

    @Test
    void anAnswerNobodyWaitsForIsRefused() {
        var answer = there.signatures()
                .answer(
                        asked.id(),
                        asking.uid(),
                        asked.uid(),
                        PairRequestStatus.DECLINED,
                        asked.name(),
                        URL_THERE,
                        FederationContractVersions.current());

        assertEquals(FederationRefusal.PAIR_ANSWER_NOT_EXPECTED, refusalOf(() -> here.outgoing()
                .receiveAnswer(answer)));
    }

    @Test
    void aQuestionSignedByAnotherStationLearnsNothing() {
        send();
        var query = here.signatures().query(asked.id(), asking.uid(), asked.uid());

        assertEquals(FederationRefusal.PAIR_REQUEST_STATION_SIGNATURE_NOT_GOOD, refusalOf(() -> there.incoming()
                .status(query)));
    }

    @Test
    void aQuestionAboutARequestNeverReceivedIsRefused() {
        var query = here.signatures().query(asking.id(), asking.uid(), asked.uid());

        assertEquals(FederationRefusal.PAIR_STATUS_NOT_HERE, refusalOf(() -> there.incoming()
                .status(query)));
    }
}
