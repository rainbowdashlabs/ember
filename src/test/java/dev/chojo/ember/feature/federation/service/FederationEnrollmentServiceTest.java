/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationContract;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes.HandshakeRequest;
import dev.chojo.ember.feature.federation.route.RemoteFederationRoutes.HandshakeResponse;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Connecting two stations that live on different instances.
 *
 * <p>Both instances run in this test. They share a database, which is what a test can offer, but
 * nothing else: each has its own base URL, its own {@link FederationService} and its own enrollment
 * service, and the only way one reaches the other is the handshake, which the stubbed HTTP client
 * carries across exactly as the network would, status code and all.
 *
 * <p>The addresses are literal public IPs on purpose. {@link RemoteUrlValidator} runs for real here,
 * so a name would mean a DNS lookup in every run; an address says the same thing and says it
 * offline, and a private one still gets refused the way a typed code pointing inwards would be.
 */
class FederationEnrollmentServiceTest extends RepositoryTestBase {

    private static final String HOST_HERE = "93.184.216.34";
    private static final String HOST_THERE = "104.16.5.7";
    private static final String HOST_INSIDE = "192.168.5.5";

    private FederationRepository federationRepo;
    private FederationSigningService signingService;
    private FederationService serviceHere;
    private FederationService serviceThere;
    private FederationEnrollmentService here;
    private FederationEnrollmentService there;
    private FederationHttpClient httpClient;

    private Station stationHere;
    private Station stationThere;
    private Station otherStationHere;

    private static Api api(String host) {
        var api = new Api();
        try {
            var field = Api.class.getDeclaredField("baseUrl");
            field.setAccessible(true);
            field.set(api, "https://" + host);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return api;
    }

    private static String codeFor(UUID stationUid, String host, String token) {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String code = "ember-"
                + encoder.encodeToString(stationUid.toString().getBytes(StandardCharsets.UTF_8))
                + "-"
                + encoder.encodeToString(host.getBytes(StandardCharsets.UTF_8));
        return token == null ? code : code + "-" + token;
    }

    /**
     * A rejection as the entering instance actually meets it: the route turns it into a status code
     * and the HTTP client turns that back into an outcome. Nothing here shortcuts that trip.
     */
    private static FederationHttpClient.HandshakeStatus overTheWire(
            FederationEnrollmentService.HandshakeRejection reason) {
        return switch (reason) {
            case INVALID_REQUEST, BAD_SIGNATURE, HOST_REFUSED -> FederationHttpClient.HandshakeStatus.REFUSED;
            case CONTRACT_MISMATCH -> FederationHttpClient.HandshakeStatus.CONTRACT_MISMATCH;
            case UNKNOWN_STATION -> FederationHttpClient.HandshakeStatus.STATION_GONE;
            case SPENT_TOKEN -> FederationHttpClient.HandshakeStatus.TOKEN_SPENT;
        };
    }

    @BeforeEach
    void setup() {
        federationRepo = new FederationRepository();
        signingService = new FederationSigningService();
        var urlValidator = new RemoteUrlValidator(new Federation(), new Demo());
        serviceHere = new FederationService(federationRepo, stationRepo, api(HOST_HERE));
        serviceThere = new FederationService(federationRepo, stationRepo, api(HOST_THERE));
        httpClient = mock(FederationHttpClient.class);
        here = new FederationEnrollmentService(
                serviceHere,
                federationRepo,
                stationRepo,
                httpClient,
                signingService,
                urlValidator,
                api(HOST_HERE),
                new Federation());
        there = new FederationEnrollmentService(
                serviceThere,
                federationRepo,
                stationRepo,
                httpClient,
                signingService,
                urlValidator,
                api(HOST_THERE),
                new Federation());

        stationHere = stationRepo.create("EnrollHere" + UUID.randomUUID());
        stationThere = stationRepo.create("EnrollThere" + UUID.randomUUID());
        otherStationHere = stationRepo.create("EnrollHereToo" + UUID.randomUUID());
    }

    @AfterEach
    void cleanup() {
        for (var station : List.of(stationHere, stationThere, otherStationHere)) {
            for (var partner : federationRepo.findPartners(station.id())) {
                federationRepo.deletePartner(partner.id());
            }
            stationRepo.delete(station.id());
        }
    }

    /** Hands every handshake for the other instance to the other instance. */
    private void letThemAnswer() {
        when(httpClient.handshake(eq("https://" + HOST_THERE), any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(1, HandshakeRequest.class);
            return switch (there.acceptHandshake(request)) {
                case FederationEnrollmentService.Handshake.Accepted accepted ->
                    new FederationHttpClient.HandshakeAttempt(
                            FederationHttpClient.HandshakeStatus.ESTABLISHED, accepted.response());
                case FederationEnrollmentService.Handshake.Rejected rejected ->
                    new FederationHttpClient.HandshakeAttempt(overTheWire(rejected.reason()), null);
            };
        });
    }

    private void letThemFail(FederationHttpClient.HandshakeStatus status) {
        when(httpClient.handshake(anyString(), any()))
                .thenReturn(new FederationHttpClient.HandshakeAttempt(status, null));
    }

    private String inviteFromThere() {
        return serviceThere.generateStationInvite(stationThere.id(), stationThere.uid());
    }

    private FederationPartner onlyPartner(Station station, UUID partnerUid) {
        var partners = federationRepo.findPartners(station.id()).stream()
                .filter(partner -> partner.partnerStationId().equals(partnerUid))
                .toList();
        assertEquals(1, partners.size(), "exactly one row for this pair");
        return partners.getFirst();
    }

    private FederationService.CodeRefusal refusal(FederationService.CodeOutcome outcome) {
        return assertInstanceOf(FederationService.CodeOutcome.Refused.class, outcome)
                .reason();
    }

    @Test
    void aCodeFromAnotherInstanceConnectsBothSides() {
        letThemAnswer();
        var code = inviteFromThere();

        var outcome = here.enterCode(stationHere.id(), code);

        assertInstanceOf(FederationService.CodeOutcome.Partnered.class, outcome);
        var ours = onlyPartner(stationHere, stationThere.uid());
        var theirs = onlyPartner(stationThere, stationHere.uid());
        assertEquals(FederationPartner.FederationStatus.ACTIVE, ours.status());
        assertEquals(FederationPartner.FederationStatus.ACTIVE, theirs.status());
        assertEquals("https://" + HOST_THERE, ours.remoteHost());
        assertEquals("https://" + HOST_HERE, theirs.remoteHost());
        assertEquals(stationThere.name(), ours.partnerStationName());
        assertEquals(stationHere.name(), theirs.partnerStationName());
    }

    @Test
    void bothSidesHoldEachOthersKeysAndVersion() {
        letThemAnswer();

        here.enterCode(stationHere.id(), inviteFromThere());

        var ours = onlyPartner(stationHere, stationThere.uid());
        var theirs = onlyPartner(stationThere, stationHere.uid());
        assertEquals(ours.publicKey(), theirs.partnerPublicKey());
        assertEquals(theirs.publicKey(), ours.partnerPublicKey());
        assertEquals(
                ours.publicKey(),
                signingService.derivePublicKey(
                        stationRepo.findById(stationHere.id()).orElseThrow().federationPrivateKey()));
        assertEquals(
                FederationContractVersions.current().core(),
                ours.federationContract().core());
        assertEquals(
                FederationContractVersions.current().core(),
                theirs.federationContract().core());
    }

    @Test
    void everyCapabilityIsOnForBothSides() {
        letThemAnswer();

        here.enterCode(stationHere.id(), inviteFromThere());

        int expected = CapabilityType.values().length * Direction.values().length;
        assertEquals(
                expected,
                federationRepo
                        .findCapabilities(
                                onlyPartner(stationHere, stationThere.uid()).id())
                        .size());
        assertEquals(
                expected,
                federationRepo
                        .findCapabilities(
                                onlyPartner(stationThere, stationHere.uid()).id())
                        .size());
    }

    /**
     * The token lives on the instance that made it and is redeemed there. A second station entering
     * the same code reaches the same delete, finds nothing to remove and gets nothing.
     */
    @Test
    void oneTokenConnectsOnePairAndNoMore() {
        letThemAnswer();
        var code = inviteFromThere();

        assertInstanceOf(FederationService.CodeOutcome.Partnered.class, here.enterCode(stationHere.id(), code));

        assertEquals(FederationService.CodeRefusal.SPENT_TOKEN, refusal(here.enterCode(otherStationHere.id(), code)));
        assertTrue(
                federationRepo.findPartners(otherStationHere.id()).isEmpty(),
                "the second station got no partnership out of it");
    }

    @Test
    void aTokenTheOtherInstanceNeverIssuedIsRefused() {
        letThemAnswer();
        var code = codeFor(stationThere.uid(), HOST_THERE, "neverissued");

        assertEquals(FederationService.CodeRefusal.SPENT_TOKEN, refusal(here.enterCode(stationHere.id(), code)));
    }

    @Test
    void anInstanceThatDoesNotAnswerSaysSo() {
        letThemFail(FederationHttpClient.HandshakeStatus.UNREACHABLE);

        assertEquals(
                FederationService.CodeRefusal.REMOTE_UNREACHABLE,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void anInstanceThatTakesTooLongSaysSo() {
        letThemFail(FederationHttpClient.HandshakeStatus.TIMEOUT);

        assertEquals(
                FederationService.CodeRefusal.REMOTE_TIMEOUT,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void anInstanceThatTurnsUsAwaySaysSo() {
        letThemFail(FederationHttpClient.HandshakeStatus.REFUSED);

        assertEquals(
                FederationService.CodeRefusal.REMOTE_REFUSED,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void aStationThatIsGoneThereSaysSo() {
        letThemFail(FederationHttpClient.HandshakeStatus.STATION_GONE);

        assertEquals(
                FederationService.CodeRefusal.REMOTE_STATION_GONE,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void anAddressThisInstanceRefusesToCallIsNamedAsSuch() {
        letThemFail(FederationHttpClient.HandshakeStatus.HOST_REFUSED);

        assertEquals(
                FederationService.CodeRefusal.HOST_REFUSED,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    /**
     * Two instances on different federation versions must not half-connect. The refusal comes from
     * the answering side, which is the one that knows both vectors.
     */
    @Test
    void versionsThatCannotTalkAreRefusedByTheAnsweringSide() {
        var code = serviceThere.generateStationInvite(stationThere.id(), stationThere.uid());
        String token = code.substring(code.lastIndexOf('-') + 1);
        var stranger = new FederationContract("not-this-build", Map.of());

        var rejected = assertInstanceOf(
                FederationEnrollmentService.Handshake.Rejected.class,
                there.acceptHandshake(
                        signedRequest(stationHere, stationThere.uid(), token, stranger, "https://" + HOST_HERE)));

        assertEquals(FederationEnrollmentService.HandshakeRejection.CONTRACT_MISMATCH, rejected.reason());
        assertTrue(federationRepo.findPartners(stationThere.id()).isEmpty(), "nothing was written");
    }

    @Test
    void versionsThatCannotTalkReachTheReaderAsSuch() {
        letThemFail(FederationHttpClient.HandshakeStatus.CONTRACT_MISMATCH);

        assertEquals(
                FederationService.CodeRefusal.CONTRACT_MISMATCH,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void anAddressPointingInsideIsNeverCalled() {
        var code = codeFor(stationThere.uid(), HOST_INSIDE, "sometoken");

        var outcome = here.enterCode(stationHere.id(), code);

        assertEquals(FederationService.CodeRefusal.HOST_REFUSED, refusal(outcome));
        assertEquals(HOST_INSIDE, ((FederationService.CodeOutcome.Refused) outcome).detail());
    }

    @Test
    void aCodeFromElsewhereWithoutATokenHasNothingToRedeem() {
        var code = codeFor(stationThere.uid(), HOST_THERE, null);

        var outcome = here.enterCode(stationHere.id(), code);

        assertEquals(FederationService.CodeRefusal.OTHER_INSTANCE, refusal(outcome));
        assertEquals(HOST_THERE, ((FederationService.CodeOutcome.Refused) outcome).detail());
    }

    @Test
    void somethingThatIsNotACodeIsRefusedBeforeAnythingIsCalled() {
        assertEquals(FederationService.CodeRefusal.MALFORMED, refusal(here.enterCode(stationHere.id(), "nonsense")));
    }

    /** A code naming a station on this instance never leaves it. */
    @Test
    void aCodeForThisInstanceTakesTheLocalPath() {
        var code = serviceHere.generateStationInvite(otherStationHere.id(), otherStationHere.uid());

        assertInstanceOf(FederationService.CodeOutcome.Partnered.class, here.enterCode(stationHere.id(), code));
    }

    @Test
    void aStationCannotJoinItselfAcrossInstances() {
        var code = codeFor(stationHere.uid(), HOST_THERE, "sometoken");

        assertEquals(FederationService.CodeRefusal.OWN_STATION, refusal(here.enterCode(stationHere.id(), code)));
    }

    @Test
    void aConnectedStationIsNotConnectedTwice() {
        letThemAnswer();
        here.enterCode(stationHere.id(), inviteFromThere());

        assertEquals(
                FederationService.CodeRefusal.ALREADY_PARTNERED,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void anAnswerWithoutAKeyIsNotAPartnership() {
        when(httpClient.handshake(anyString(), any()))
                .thenReturn(new FederationHttpClient.HandshakeAttempt(
                        FederationHttpClient.HandshakeStatus.ESTABLISHED,
                        new HandshakeResponse(
                                stationThere.uid(),
                                "Somebody",
                                "https://" + HOST_THERE,
                                FederationContractVersions.current(),
                                null)));

        assertEquals(
                FederationService.CodeRefusal.REMOTE_REFUSED,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void anAnswerThatIsNothingAtAllIsNotAPartnership() {
        when(httpClient.handshake(anyString(), any()))
                .thenReturn(new FederationHttpClient.HandshakeAttempt(
                        FederationHttpClient.HandshakeStatus.ESTABLISHED, null));

        assertEquals(
                FederationService.CodeRefusal.REMOTE_REFUSED,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    /**
     * The other side may name a fuller address than the code carried, and that is taken. It may not
     * name a different host: that would be an answer redirecting a connection somebody else asked
     * for.
     */
    @Test
    void aFullerAddressOnTheSameHostIsTaken() {
        answerAnnouncing("https://" + HOST_THERE + ":8443");

        here.enterCode(stationHere.id(), inviteFromThere());

        assertEquals(
                "https://" + HOST_THERE + ":8443",
                onlyPartner(stationHere, stationThere.uid()).remoteHost());
    }

    @Test
    void anAddressOnAnotherHostIsIgnored() {
        answerAnnouncing("https://" + HOST_HERE);

        here.enterCode(stationHere.id(), inviteFromThere());

        assertEquals(
                "https://" + HOST_THERE,
                onlyPartner(stationHere, stationThere.uid()).remoteHost());
    }

    @Test
    void anAddressPointingInsideIsIgnored() {
        answerAnnouncing("https://" + HOST_INSIDE);

        here.enterCode(stationHere.id(), inviteFromThere());

        assertEquals(
                "https://" + HOST_THERE,
                onlyPartner(stationHere, stationThere.uid()).remoteHost());
    }

    @Test
    void anAddressThatIsNoAddressIsIgnored() {
        answerAnnouncing("   ");

        here.enterCode(stationHere.id(), inviteFromThere());

        assertEquals(
                "https://" + HOST_THERE,
                onlyPartner(stationHere, stationThere.uid()).remoteHost());
    }

    /**
     * A station signs everything it federates with one key pair. Joining a second instance must
     * therefore leave the pair it already has alone.
     */
    @Test
    void asecondPartnershipKeepsTheStationsKeyPair() {
        letThemAnswer();
        here.enterCode(stationHere.id(), inviteFromThere());
        String key = stationRepo.findById(stationHere.id()).orElseThrow().federationPrivateKey();
        assertNotNull(key);

        letThemFail(FederationHttpClient.HandshakeStatus.UNREACHABLE);
        here.enterCode(stationHere.id(), codeFor(UUID.randomUUID(), HOST_THERE, "sometoken"));

        assertEquals(key, stationRepo.findById(stationHere.id()).orElseThrow().federationPrivateKey());
    }

    @Test
    void aHandshakeAskingAStationToFederateWithItselfIsTurnedAway() {
        var code = inviteFromThere();
        String token = code.substring(code.lastIndexOf('-') + 1);

        var rejected = assertInstanceOf(
                FederationEnrollmentService.Handshake.Rejected.class,
                there.acceptHandshake(signedRequest(
                        stationThere,
                        stationThere.uid(),
                        token,
                        FederationContractVersions.current(),
                        "https://" + HOST_HERE)));

        assertEquals(FederationEnrollmentService.HandshakeRejection.INVALID_REQUEST, rejected.reason());
    }

    /** An answer naming a different station than the code did is not the partnership that was asked for. */
    @Test
    void anAnswerFromAnotherStationIsNotAPartnership() {
        when(httpClient.handshake(anyString(), any()))
                .thenReturn(new FederationHttpClient.HandshakeAttempt(
                        FederationHttpClient.HandshakeStatus.ESTABLISHED,
                        new HandshakeResponse(
                                UUID.randomUUID(),
                                "Jemand anders",
                                "https://" + HOST_THERE,
                                FederationContractVersions.current(),
                                "einSchluessel")));

        assertEquals(
                FederationService.CodeRefusal.REMOTE_REFUSED,
                refusal(here.enterCode(stationHere.id(), inviteFromThere())));
    }

    @Test
    void aHandshakeMissingWhatItNeedsIsTurnedAway() {
        var empty = new HandshakeRequest(null, null, null, null, null, null, null, null);

        assertEquals(
                FederationEnrollmentService.HandshakeRejection.INVALID_REQUEST,
                assertInstanceOf(FederationEnrollmentService.Handshake.Rejected.class, there.acceptHandshake(empty))
                        .reason());
    }

    @Test
    void aHandshakeWithASignatureThatDoesNotFitIsTurnedAway() {
        var code = inviteFromThere();
        String token = code.substring(code.lastIndexOf('-') + 1);
        var signed = signedRequest(
                stationHere, stationThere.uid(), token, FederationContractVersions.current(), "https://" + HOST_HERE);
        var tampered = new HandshakeRequest(
                signed.stationUid(),
                signed.targetStationUid(),
                signed.stationName(),
                signed.baseUrl(),
                signed.contract(),
                signed.publicKey(),
                "adifferenttoken",
                signed.signature());

        assertEquals(
                FederationEnrollmentService.HandshakeRejection.BAD_SIGNATURE,
                assertInstanceOf(FederationEnrollmentService.Handshake.Rejected.class, there.acceptHandshake(tampered))
                        .reason());
    }

    @Test
    void aHandshakeCarryingSomethingThatIsNoKeyIsTurnedAway() {
        var broken = new HandshakeRequest(
                stationHere.uid(),
                stationThere.uid(),
                stationHere.name(),
                "https://" + HOST_HERE,
                FederationContractVersions.current(),
                "not-a-key",
                "sometoken",
                "not-a-signature");

        assertEquals(
                FederationEnrollmentService.HandshakeRejection.BAD_SIGNATURE,
                assertInstanceOf(FederationEnrollmentService.Handshake.Rejected.class, there.acceptHandshake(broken))
                        .reason());
    }

    @Test
    void aHandshakeNamingAnAddressPointingInsideIsTurnedAway() {
        var code = inviteFromThere();
        String token = code.substring(code.lastIndexOf('-') + 1);

        var rejected = assertInstanceOf(
                FederationEnrollmentService.Handshake.Rejected.class,
                there.acceptHandshake(signedRequest(
                        stationHere,
                        stationThere.uid(),
                        token,
                        FederationContractVersions.current(),
                        "https://" + HOST_INSIDE)));

        assertEquals(FederationEnrollmentService.HandshakeRejection.HOST_REFUSED, rejected.reason());
    }

    @Test
    void aHandshakeForAStationThatIsGoneIsTurnedAway() {
        var rejected = assertInstanceOf(
                FederationEnrollmentService.Handshake.Rejected.class,
                there.acceptHandshake(signedRequest(
                        stationHere,
                        UUID.randomUUID(),
                        "sometoken",
                        FederationContractVersions.current(),
                        "https://" + HOST_HERE)));

        assertEquals(FederationEnrollmentService.HandshakeRejection.UNKNOWN_STATION, rejected.reason());
    }

    @Test
    void aHandshakeThatWorkedIsNoRefusal() {
        assertThrows(
                IllegalStateException.class,
                () -> FederationEnrollmentService.refusalFor(FederationHttpClient.HandshakeStatus.ESTABLISHED));
    }

    @Test
    void theSignedPayloadNamesEverythingTheExchangeDecides() {
        var request = signedRequest(
                stationHere,
                stationThere.uid(),
                "atoken",
                FederationContractVersions.current(),
                "https://" + HOST_HERE);
        String payload = FederationEnrollmentService.enrollmentPayload(request);

        assertTrue(payload.contains(stationHere.uid().toString()));
        assertTrue(payload.contains(stationThere.uid().toString()));
        assertTrue(payload.contains(FederationContractVersions.current().core()));
        assertTrue(payload.contains("atoken"));
        assertTrue(payload.contains("https://" + HOST_HERE));
    }

    /** Answers the handshake for real, but has the other instance announce the given address. */
    private void answerAnnouncing(String announced) {
        when(httpClient.handshake(eq("https://" + HOST_THERE), any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(1, HandshakeRequest.class);
            var accepted = assertInstanceOf(
                    FederationEnrollmentService.Handshake.Accepted.class, there.acceptHandshake(request));
            var answer = accepted.response();
            return new FederationHttpClient.HandshakeAttempt(
                    FederationHttpClient.HandshakeStatus.ESTABLISHED,
                    new HandshakeResponse(
                            answer.stationUid(),
                            answer.stationName(),
                            announced,
                            answer.contract(),
                            answer.publicKey()));
        });
    }

    private HandshakeRequest signedRequest(
            Station station, UUID targetUid, String token, FederationContract contract, String baseUrl) {
        var keyPair = serviceHere.generateKeyPair();
        var unsigned = new HandshakeRequest(
                station.uid(),
                targetUid,
                station.name(),
                baseUrl,
                contract,
                serviceHere.encodePublicKey(keyPair),
                token,
                "");
        String signature = signingService.signEnrollmentPayload(
                FederationEnrollmentService.enrollmentPayload(unsigned), keyPair.getPrivate());
        return new HandshakeRequest(
                unsigned.stationUid(),
                unsigned.targetStationUid(),
                unsigned.stationName(),
                unsigned.baseUrl(),
                unsigned.contract(),
                unsigned.publicKey(),
                unsigned.token(),
                signature);
    }

    @Test
    void aStationWithNoKeyYetGetsOne() {
        assertFalse(
                stationRepo.findById(stationHere.id()).orElseThrow().federationPrivateKey() != null
                        && !stationRepo
                                .findById(stationHere.id())
                                .orElseThrow()
                                .federationPrivateKey()
                                .isBlank(),
                "a fresh station signs nothing yet");
        letThemAnswer();

        here.enterCode(stationHere.id(), inviteFromThere());

        assertNotNull(stationRepo.findById(stationHere.id()).orElseThrow().federationPrivateKey());
    }
}
