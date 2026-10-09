/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.PublicIdModule;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.events.entity.PartnerDocumentToSign;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventAttachmentRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.events.service.FederatedRegistrantService;
import dev.chojo.ember.feature.federation.FederationTestTransport;
import dev.chojo.ember.feature.federation.contract.FederationContractVersions;
import dev.chojo.ember.feature.federation.contract.FederationRequest;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationSigningService;
import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.StationKeyStore;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationHandler;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.generator.service.MemberNeutralTemplates;
import dev.chojo.ember.feature.generator.service.RequirementSignatures;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.entity.AgreementNoticeKind;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.PartnerAgreement;
import dev.chojo.ember.feature.signing.entity.PartnerAgreementState;
import dev.chojo.ember.feature.signing.entity.PartnerSignerDocument;
import dev.chojo.ember.feature.signing.entity.RemoteAgreementField;
import dev.chojo.ember.feature.signing.entity.RemoteAgreementNotice;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.repository.PartnerAgreementRepository;
import dev.chojo.ember.feature.signing.repository.PartnerAuthorityRepository;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.feature.signing.repository.SigningKeyRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.TestFederationServices;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A shared appointment asking the members of a partner station to sign a document, between two
 * installations.
 *
 * <p>The station holding the appointment and the member's home station each act as their own installation:
 * they reach each other only over the federation's HTTP client, stubbed here to hand every request, as JSON,
 * to the serving function of the station it names, as the other installation's {@code /remote} route would.
 * Each knows the other by the federation key it holds from the pairing, and the station holding the
 * appointment checks every sealed copy against the authorities the home station stated to it under that key.
 */
class PartnerAgreementsTest extends GeneratorTestBase {
    private static final Instant START =
            Instant.now().plus(Duration.ofDays(20)).truncatedTo(ChronoUnit.DAYS).plus(Duration.ofHours(9));
    private static final LocalDate DAY = LocalDate.ofInstant(START, ZoneOffset.UTC);
    private static final String ORGANISER_HOST = "https://organiser.example";
    private static final String HOME_HOST = "https://home.example";
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static final FederationRepository federationRepo = new FederationRepository();
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();
    private static final SigningEvidenceRepository evidenceRepo = new SigningEvidenceRepository();
    private static final PartnerAgreementRepository agreementRepo = new PartnerAgreementRepository();
    private static final PartnerSigningRepository linkRepo = new PartnerSigningRepository();
    private static final EventFederationRepository shareRepo = new EventFederationRepository();
    private static final JsonMapper API = JsonMapper.builder()
            .addModule(PublicIdModule.forApi(stationRepo, new ClusterRepository()))
            .build();
    private static final JsonMapper WIRE = OutboundHttp.lenientMapper(PublicIdModule.forPartnerResponses());
    private static final AtomicBoolean ORGANISER_DOWN = new AtomicBoolean();

    private static Wiring organiser;
    private static Station home;
    private static FederationPartner organiserSide;
    private static FederationPartner homeSide;
    private static FederationEndpoints endpoints;
    private static ExecutorService executor;
    private static EventFederationService events;
    private static EventRequirementService requirements;
    private static PartnerAgreements agreements;
    private static PartnerDeliveries deliveries;
    private static PartnerSignatures signatures;
    private static SignatureFieldService fields;
    private static SigningStateSealer sealer;
    private static SignatureWithdrawals withdrawals;
    private static StationMember manager;
    private static int loginPermission;

    private StationEvent camp;
    private int consent;

    @BeforeAll
    static void twoInstallations() {
        organiser = wire(stationRepo.create("Veranstalterwache " + System.nanoTime()));
        home = stationRepo.create("Heimatwache " + System.nanoTime());
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        manager = stationMemberRepo.create(
                organiser.station().id(),
                accountRepo
                        .create("organiser-manager-" + System.nanoTime() + "@example.com", "Maria", "Leitung")
                        .id());
        stationMemberRepo.grantPermission(
                manager.id(),
                stationMemberRepo
                        .findPermissionByName(StationPermission.EVENT_MANAGER)
                        .orElseThrow()
                        .id());

        StationKeyStore federationKeys = TestStationKeys.store();
        String organiserKey = federationKeys.ensurePublicKey(organiser.station().id());
        String homeKey = federationKeys.ensurePublicKey(home.id());
        var contract = FederationContractVersions.current();
        organiserSide = federationRepo.createRemotePartner(
                organiser.station().id(), home.uid(), organiserKey, homeKey, HOME_HOST, "Heimatwache", contract);
        homeSide = federationRepo.createRemotePartner(
                home.id(), organiser.station().uid(), homeKey, organiserKey, ORGANISER_HOST, "Veranstalter", contract);

        var httpClient = mock(FederationHttpClient.class);
        when(httpClient.canSign(anyInt())).thenReturn(true);
        var transport = new FederationTestTransport(httpClient, federationRepo, stationRepo);
        executor = Executors.newVirtualThreadPerTaskExecutor();

        var keys = new SigningKeyRepository();
        var wrap = new SigningKeyWrap(Base64.getEncoder().encodeToString(new byte[32]));
        var revocations = new StationKeyRevocations(keys, new RevocationLists(), wrap);
        var pins = new PartnerAuthorityRepository();
        var authorities = new PartnerAuthorities(
                transport.transport(),
                federationRepo,
                stationRepo,
                TestStationKeys.signer(),
                new FederationSigningService(),
                keys,
                revocations,
                pins,
                executor,
                Clock.systemUTC(),
                PartnerAuthorities.BUDGET);
        var validator = new PartnerSealValidator(
                authorities, pins, new SealVerifier(keys, revocations, new SealedVersionRepository(), pins, List.of()));

        EventCrudService crud = newEventServices(new DomainEventBus(Set.of())).crud();
        var media = mock(MediaLibraryService.class);
        events = new EventFederationService(
                shareRepo,
                TestFederationServices.of(federationRepo, stationRepo),
                transport.transport(),
                federationRepo,
                stationRepo,
                crud,
                newCommentService(mock(DomainEventBus.class)),
                memberNameResolver,
                new FederationFanout(new TaskScheduler()),
                new FederationEntityResolver(federationRepo),
                new EventAttachmentService(new EventAttachmentRepository(), media),
                new EventFieldService(
                        eventFieldRepo,
                        stationMemberRepo,
                        memberEligibility,
                        eventRepo,
                        attendanceRepo,
                        eventFieldRegistrationService),
                occurrenceCalendar,
                media,
                new Api());

        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var requirementRepo = new EventRequirementRepository();
        var neutral = new MemberNeutralTemplates(organiser.templates());
        requirements = new EventRequirementService(
                requirementRepo, organiser.templates(), neutral, shareRepo, new DomainEventBus(Set.of()));
        var appointments = new AppointmentDocumentService(
                requirementRepo,
                new PaperSubmissionRepository(),
                organiser.templates(),
                organiser.generator(),
                organiser.generation(),
                guardianPolicy,
                eventRegistrationRepo,
                eventFieldRepo,
                memberNameResolver,
                organiser.issuers(),
                new EventRestrictionService(eventRepo, restrictionService),
                RequirementSignatures.NONE);
        var notices = TestNotices.notices(
                newNotifier(), emailQueueRepo, stationRepo, stationMemberRepo, accountRepo, memberDocumentRepo);
        agreements = new PartnerAgreements(
                crud,
                events,
                new FederatedRegistrantService(
                        federationRepo, stationRepo, stationMemberRepo, memberIdentityFactory, events),
                occurrenceCalendar,
                appointments,
                neutral,
                agreementRepo,
                validator,
                federationRepo,
                memberNameResolver,
                notices);

        var documents = organiser.documents();
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, documents, guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        var requests = new SignatureRequestService(
                requestRepo,
                evidenceRepo,
                organiser.log(),
                memberDocumentRepo,
                documents,
                stationMemberRepo,
                memberNameResolver,
                guardianPolicy,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                notices,
                new TemplateDocumentStatements(organiser.generator()));
        fields = new SignatureFieldService(
                requestRepo,
                evidenceRepo,
                requests,
                guards,
                guardianPolicy,
                memberNameResolver,
                notices,
                completed -> {});
        deliveries = new PartnerDeliveries(
                linkRepo,
                requestRepo,
                memberDocumentRepo,
                documents,
                stationMemberRepo,
                federationRepo,
                transport.transport(),
                Runnable::run,
                organiser.clock());
        signatures = new PartnerSignatures(
                new FederationEntityResolver(federationRepo),
                transport.transport(),
                stationMemberRepo,
                guardianPolicy,
                memberNameResolver,
                documents,
                newDocumentIntake(),
                requests,
                requestRepo,
                linkRepo,
                deliveries,
                new RequirementSignatureStates(requestRepo, requests, new WithdrawalRights(guardianPolicy)));
        sealer = TestSealing.stateSealer(memberDocumentRepo, documents, stationRepo, memberNameResolver, deliveries);
        withdrawals = new SignatureWithdrawals(
                requestRepo,
                requests,
                new WithdrawalRights(guardianPolicy),
                memberNameResolver,
                sealer,
                notices,
                eventRepo,
                eventRegistrationRepo,
                mock(AppointmentSignatures.class));

        endpoints = transport.serve(events, authorities, agreements);
        routeOverHttp(httpClient);
    }

    @AfterAll
    static void cleanup() {
        executor.shutdownNow();
        stationRepo.delete(organiser.station().id());
        stationRepo.delete(home.id());
    }

    @BeforeEach
    void sharedAppointment() {
        ORGANISER_DOWN.set(false);
        camp = eventRepo.create(
                organiser.station().id(),
                "Zeltlager " + NAMES.incrementAndGet(),
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(8)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        shareRepo.setShare(camp.id(), ShareScope.ALL_PARTNERS);
        consent = organiser
                .templates()
                .create(
                        organiser.owner(),
                        letter("Einverständnis " + NAMES.incrementAndGet())
                                .body(List.of(
                                        row(text("Ich nehme an {{event.name}} bei {{station.name}} teil.")),
                                        row(signature(SignatureRole.PARTICIPANT, "Teilnehmende Person"))))
                                .forAppointments(true)
                                .build(),
                        manager.id())
                .id();
        requirements.setForEvent(organiser.owner(), camp.id(), List.of(consent));
    }

    @AfterEach
    void organiserUp() {
        ORGANISER_DOWN.set(false);
    }

    /**
     * A member of the partner registers and signs at home: the copy drawn once for the date is filed in the
     * member's documents at home byte for byte, the organiser hears it was taken on, the member signs with
     * the home station's proof and seal, and the sealed copy goes back, is checked against the authorities
     * the home station stated, and is kept with the registration, locked.
     */
    @Test
    void aPartnersMemberSignsAtHomeAndTheOrganiserKeepsTheSealedCopy() {
        var member = homeMember("Alex", "Albers");

        var toSign = register(member);

        assertEquals(1, toSign.size());
        var asked = toSign.getFirst();
        assertEquals(member.id(), asked.memberId());
        assertEquals(RequirementSignatureState.OPEN, asked.signature().state());
        assertTrue(asked.signature().fields().getFirst().yours(), "the member signs their own field");
        var request = requestRepo.findByUid(asked.signature().requestUid()).orElseThrow();
        assertNull(request.generationId(), "nothing here generated it");
        var handedOut = agreementRepo.handedOut(camp.id(), DAY, consent, 1).orElseThrow();
        assertEquals(handedOut.sha256(), request.contentSha256(), "the member signs the organiser's very bytes");
        var filed = memberDocumentRepo.findById(request.documentId()).orElseThrow();
        assertArrayEquals(handedOut.content(), organiser.documents().read(filed).orElseThrow());
        assertEquals(PartnerAgreementState.ASKED, documentOf(member).state());

        sign(request, member);

        var standing = documentOf(member);
        assertEquals(PartnerAgreementState.SIGNED, standing.state());
        assertTrue(standing.complete());
        assertEquals(1, standing.copies());
        var sealedAtHome = organiser
                .documents()
                .read(memberDocumentRepo.findById(request.documentId()).orElseThrow())
                .orElseThrow();
        var kept = agreements.latestCopy(managing(), standing.agreementId());
        assertArrayEquals(sealedAtHome, kept.pdf(), "the organiser keeps the copy sealed at home");
        assertEquals(
                Sha256.hex(sealedAtHome),
                linkRepo.forRequest(request.id()).orElseThrow().deliveredSha256());
        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
    }

    /** Every partner's member signs the one copy drawn for the date, however many register. */
    @Test
    void theCopyIsDrawnOncePerDateForEveryPartnersMember() {
        var first = register(homeMember("Bea", "Berg")).getFirst();
        var second = register(homeMember("Cem", "Cetin")).getFirst();

        var firstRequest = requestRepo.findByUid(first.signature().requestUid()).orElseThrow();
        var secondRequest =
                requestRepo.findByUid(second.signature().requestUid()).orElseThrow();
        assertEquals(firstRequest.contentSha256(), secondRequest.contentSha256());
        assertEquals(
                agreementRepo
                        .handedOut(camp.id(), DAY, consent, 1)
                        .orElseThrow()
                        .sha256(),
                firstRequest.contentSha256());
    }

    /** Registering again for the same date takes nothing on twice. */
    @Test
    void registeringAgainTakesNothingOnTwice() {
        var member = homeMember("Dana", "Dorn");
        var first = register(member).getFirst();

        var again = signatures.registered(at(member), organiser.station().uid(), camp.id(), DAY, member.uid());

        assertEquals(
                first.signature().requestUid(), again.getFirst().signature().requestUid());
    }

    /** Giving the place up lets the signature still open go; nobody is asked for it any more. */
    @Test
    void withdrawingTheRegistrationLetsTheOpenSignaturesGo() {
        var member = homeMember("Emil", "Eck");
        var asked = register(member).getFirst();

        events.withdrawFederatedRegistration(home.id(), organiser.station().uid(), camp.id(), member.uid(), DAY);
        signatures.withdrawn(at(member), organiser.station().uid(), camp.id(), DAY, member.uid());

        var request = requestRepo.findByUid(asked.signature().requestUid()).orElseThrow();
        assertEquals(RequestState.WITHDRAWN, request.state());
        assertTrue(
                requestRepo.fieldsOf(request.id()).stream().allMatch(field -> field.state() == FieldState.WITHDRAWN));
    }

    /**
     * A partner that never takes the document on, as an older installation or one that cannot sign: its
     * member is registered all the same and counts as signature missing, and the organiser confirms a signed
     * paper copy for them. Once a sealed copy came back, a paper copy is no longer confirmed.
     */
    @Test
    void aPartnerThatCannotSignLeavesTheSignatureMissingUntilAPaperCopyIsConfirmed() {
        var member = homeMember("Finn", "Fuchs");
        events.registerForFederatedEvent(home.id(), organiser.station().uid(), camp.id(), member.uid(), DAY);

        var missing = documentOf(member);
        assertEquals(PartnerAgreementState.MISSING, missing.state());
        assertNull(missing.agreementId());

        var registration = onlyRegistrationOf(member);
        var confirmed = agreements.confirmPaper(managing(), camp, registration, consent);

        assertEquals(PartnerAgreementState.PAPER_CONFIRMED, confirmed.state());
        assertEquals(memberNameResolver.official(manager.id()), confirmed.confirmedByName());
        assertEquals(PartnerAgreementState.PAPER_CONFIRMED, documentOf(member).state());

        var signer = homeMember("Gina", "Graf");
        var request = requestOf(register(signer).getFirst());
        sign(request, signer);
        refused(
                DocumentRefusal.PARTNER_AGREEMENT_ALREADY_SIGNED,
                () -> agreements.confirmPaper(managing(), camp, onlyRegistrationOf(signer), consent));
    }

    /**
     * A copy the organiser could not be reached with is sent again later, after the time a failure waits, and
     * taken then.
     */
    @Test
    void aCopyTheOrganiserMissedIsSentAgainLater() {
        var member = homeMember("Hanna", "Hahn");
        var request = requestOf(register(member).getFirst());
        ORGANISER_DOWN.set(true);

        sign(request, member);

        var link = linkRepo.forRequest(request.id()).orElseThrow();
        assertEquals(1, link.deliveryAttempts());
        assertNull(link.deliveredSha256());
        assertEquals(PartnerAgreementState.ASKED, documentOf(member).state());

        ORGANISER_DOWN.set(false);
        assertEquals(0, deliveries.sweep(), "the next try is not due yet");
        organiser.clock().advance(PartnerDeliveries.delayAfter(0).plusSeconds(1));
        assertTrue(deliveries.sweep() >= 1);

        assertEquals(PartnerAgreementState.SIGNED, documentOf(member).state());
        assertEquals(0, linkRepo.forRequest(request.id()).orElseThrow().deliveryAttempts());
    }

    /**
     * What a partner sends is taken only for an appointment shared with it, a member it registered, a copy
     * the organiser handed out, and a sealed file whose seal is the partner's own.
     */
    @Test
    void whatThePartnerSendsIsCheckedBeforeItIsTaken() throws IOException {
        var member = homeMember("Ida", "Imhof");
        var request = requestOf(register(member).getFirst());
        var serving = new ServingPartner(organiserSide, home.uid());
        var handedOut = agreementRepo.handedOut(camp.id(), DAY, consent, 1).orElseThrow();

        refused(
                EventRefusal.PARTNER_AGREEMENT_NOTICE_NOT_REGISTERED,
                () -> agreements.take(serving, camp.id(), signed(UUID.randomUUID(), handedOut.sha256(), "e30=")));
        refused(
                EventRefusal.PARTNER_AGREEMENT_NOTICE_UNKNOWN_DOCUMENT,
                () -> agreements.take(serving, camp.id(), signed(member.uid(), "0".repeat(64), "e30=")));
        refused(
                EventRefusal.PARTNER_AGREEMENT_COPY_UNREADABLE,
                () -> agreements.take(serving, camp.id(), signed(member.uid(), handedOut.sha256(), "kein base64")));
        refused(
                EventRefusal.PARTNER_AGREEMENT_SEAL_REFUSED,
                () -> agreements.take(
                        serving, camp.id(), signed(member.uid(), handedOut.sha256(), encoded(handedOut.content()))));
        var sealedByOrganiser = sealedByOrganiser(handedOut.content());
        refused(
                EventRefusal.PARTNER_AGREEMENT_SEAL_REFUSED,
                () -> agreements.take(
                        serving, camp.id(), signed(member.uid(), handedOut.sha256(), encoded(sealedByOrganiser))));
        assertEquals(PartnerAgreementState.ASKED, documentOf(member).state(), "nothing refused was taken");

        var otherCamp = eventRepo.create(
                organiser.station().id(),
                "Nicht geteilt " + NAMES.incrementAndGet(),
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(1)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        refused(EventRefusal.PARTNER_AGREEMENTS_NOT_SHARED, () -> agreements.handOut(serving, otherCamp.id(), DAY));
        assertEquals(
                RequestState.OPEN,
                requestRepo.findById(request.id()).orElseThrow().state());
    }

    /** A row holding a sealed copy outlives its appointment until its retention is over, then the sweep takes it. */
    @Test
    void aSignedCopyIsKeptForItsRetentionAndThenDeleted() {
        var member = homeMember("Jan", "Jung");
        sign(requestOf(register(member).getFirst()), member);
        var standing = documentOf(member);

        var early = new PartnerAgreements(
                newEventServices(new DomainEventBus(Set.of())).crud(),
                events,
                mock(FederatedRegistrantService.class),
                occurrenceCalendar,
                mock(AppointmentDocumentService.class),
                mock(MemberNeutralTemplates.class),
                agreementRepo,
                mock(PartnerSealValidator.class),
                federationRepo,
                memberNameResolver,
                mock(SignatureNotices.class),
                Clock.systemUTC());
        early.sweep();
        assertNotNull(agreementRepo
                .find(organiser.station().id(), standing.agreementId())
                .orElse(null));
        assertThrows(
                RuntimeException.class,
                () -> agreementRepo.delete(standing.agreementId()),
                "the database keeps a signed copy before its retention is over");

        var over = new PartnerAgreement.Key(
                organiser.station().id(),
                camp.id(),
                DAY.minusYears(2),
                consent,
                "Einverständnis von damals",
                organiserSide.id(),
                home.uid(),
                "Heimatwache",
                UUID.randomUUID(),
                12,
                Instant.now().minus(Duration.ofDays(1)));
        int old = agreementRepo.report(over, PartnerAgreementState.SIGNED, "a".repeat(64), true);
        agreementRepo.addCopy(old, "b".repeat(64), new byte[] {1}, "[]", true);

        assertTrue(early.sweep() >= 1);
        assertTrue(agreementRepo.find(organiser.station().id(), old).isEmpty(), "past its retention it is deleted");
        assertNotNull(agreementRepo
                .find(organiser.station().id(), standing.agreementId())
                .orElse(null));
    }

    /**
     * A member who signed at home withdraws the agreement there while the organiser cannot be reached: the
     * sealed version recording the withdrawal goes out again later, is checked and kept beside the signed copy,
     * which stays, the document shows as withdrawn, the registration is flagged, and whoever runs the
     * appointment is told.
     */
    @Test
    void aWithdrawalAtHomeReachesTheOrganiserOnceItIsUpAgain() {
        var member = homeMember("Karla", "Klee");
        var request = requestOf(register(member).getFirst());
        sign(request, member);
        var signed = documentOf(member);
        byte[] signedCopy =
                agreements.latestCopy(managing(), signed.agreementId()).pdf();
        ORGANISER_DOWN.set(true);

        withdrawals.requireOwnedThenWithdraw(
                at(member), request.uid(), "Ich fahre doch nicht mit.", new SigningCircumstances(null, null));

        assertEquals(PartnerAgreementState.SIGNED, documentOf(member).state());
        assertEquals(1, linkRepo.forRequest(request.id()).orElseThrow().deliveryAttempts());
        ORGANISER_DOWN.set(false);
        organiser.clock().advance(PartnerDeliveries.delayAfter(0).plusSeconds(1));
        assertTrue(deliveries.sweep() >= 1);

        var withdrawn = documentOf(member);
        assertEquals(PartnerAgreementState.WITHDRAWN, withdrawn.state());
        assertEquals(2, withdrawn.copies(), "the signed copy stays beside the withdrawal");
        var withdrawal = requestRepo.withdrawalOf(request.id()).orElseThrow();
        byte[] kept = agreements.latestCopy(managing(), withdrawn.agreementId()).pdf();
        assertEquals(withdrawal.sealedSha256(), Sha256.hex(kept));
        assertNotEquals(Sha256.hex(signedCopy), Sha256.hex(kept));
        assertNotNull(events.findRegistration(camp.id(), organiserSide.id(), member.uid(), DAY)
                .orElseThrow()
                .agreementWithdrawnAt());
        assertTrue(notificationsOf(manager).contains("PARTNER_SIGNATURE_WITHDRAWN"));
        assertEquals(
                withdrawal.sealedSha256(),
                linkRepo.forRequest(request.id()).orElseThrow().deliveredSha256());
    }

    /**
     * A withdrawal is taken only with the partner's own seal on it: an unsealed file or one sealed by somebody
     * else is refused, and the agreement stays signed with the registration unflagged.
     */
    @Test
    void aWithdrawalWithoutThePartnersSealIsRefused() throws IOException {
        var member = homeMember("Lars", "Lind");
        sign(requestOf(register(member).getFirst()), member);
        var serving = new ServingPartner(organiserSide, home.uid());
        var handedOut = agreementRepo.handedOut(camp.id(), DAY, consent, 1).orElseThrow();

        refused(
                EventRefusal.PARTNER_AGREEMENT_SEAL_REFUSED,
                () -> agreements.take(
                        serving,
                        camp.id(),
                        notice(
                                AgreementNoticeKind.WITHDRAWN,
                                member.uid(),
                                handedOut.sha256(),
                                encoded(handedOut.content()))));
        var forged = encoded(sealedByOrganiser(handedOut.content()));
        refused(
                EventRefusal.PARTNER_AGREEMENT_SEAL_REFUSED,
                () -> agreements.take(
                        serving,
                        camp.id(),
                        notice(AgreementNoticeKind.WITHDRAWN, member.uid(), handedOut.sha256(), forged)));

        assertEquals(PartnerAgreementState.SIGNED, documentOf(member).state());
        assertNull(events.findRegistration(camp.id(), organiserSide.id(), member.uid(), DAY)
                .orElseThrow()
                .agreementWithdrawnAt());
    }

    private List<PartnerDocumentToSign> register(StationMember member) {
        events.registerForFederatedEvent(home.id(), organiser.station().uid(), camp.id(), member.uid(), DAY);
        return signatures.registered(at(member), organiser.station().uid(), camp.id(), DAY, member.uid());
    }

    private static SignatureRequest requestOf(PartnerDocumentToSign asked) {
        return requestRepo.findByUid(asked.signature().requestUid()).orElseThrow();
    }

    private static void sign(SignatureRequest request, StationMember signer) {
        var field = requestRepo.fieldsOf(request.id()).getFirst();
        var act = new SigningAct(
                request.uid(),
                Signer.accountHolder(signer.accountId()),
                memberNameResolver.official(signer.id()),
                null,
                field.fieldName(),
                field.statement(),
                HexFormat.of().parseHex(request.contentSha256()),
                List.of(),
                new byte[32],
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "203.0.113.0",
                "Test Browser");
        fields.record(at(signer), new CompletedSigning(SignatureLevel.SIMPLE, new SigningEvidence.TotpUnbound(act)));
        sealer.sealLatest(request.id());
    }

    private PartnerSignerDocument documentOf(StationMember member) {
        return agreements.signers(camp, DAY).stream()
                .filter(signer -> signer.member() != null
                        && member.uid().equals(signer.member().memberUid()))
                .findFirst()
                .orElseThrow()
                .documents()
                .getFirst();
    }

    private int onlyRegistrationOf(StationMember member) {
        return events.findRegistration(camp.id(), organiserSide.id(), member.uid(), DAY)
                .orElseThrow()
                .id();
    }

    private RemoteAgreementNotice signed(UUID member, String contentSha256, String sealedPdf) {
        return notice(AgreementNoticeKind.SIGNED, member, contentSha256, sealedPdf);
    }

    private RemoteAgreementNotice notice(
            AgreementNoticeKind kind, UUID member, String contentSha256, String sealedPdf) {
        return new RemoteAgreementNotice(
                member,
                DAY,
                consent,
                1,
                contentSha256,
                kind,
                sealedPdf,
                List.of(new RemoteAgreementField("participant", FieldState.SIGNED, Instant.now())),
                true);
    }

    private static String encoded(byte[] pdf) {
        return Base64.getEncoder().encodeToString(pdf);
    }

    private static byte[] sealedByOrganiser(byte[] content) throws IOException {
        var key = organiserKey();
        return TestSealing.withoutTimestamps()
                .sealWithoutTimestamp(content, key.privateKey(), key.chain())
                .pdf();
    }

    private static String notificationsOf(StationMember member) {
        return query("SELECT string_agg(type, ',') AS types FROM notification WHERE member_id = :member_id;")
                .single(call().bind("member_id", member.id()))
                .map(row -> String.valueOf(row.getString("types")))
                .first()
                .orElse("");
    }

    private static SealingKey organiserKey() {
        var keys = new SigningKeyRepository();
        var wrap = new SigningKeyWrap(Base64.getEncoder().encodeToString(new byte[32]));
        return new StationSigningKeys(keys, new SigningCertificates(), wrap, stationRepo, ORGANISER_HOST)
                .forStation(organiser.station().id());
    }

    private static StationMember homeMember(String first, String last) {
        var account = accountRepo.create("partner-signing-" + NAMES.incrementAndGet() + "@test.com", first, last);
        var member = stationMemberRepo.create(home.id(), account.id());
        stationMemberRepo.grantPermission(member.id(), loginPermission);
        return member;
    }

    private static StationSession at(StationMember member) {
        return stationSession(member, StationPermission.LOGIN);
    }

    private static StationSession managing() {
        return stationSession(manager, StationPermission.EVENT_REGISTRATION);
    }

    private static void refused(Refusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    /**
     * Hands every request the stubbed client sends to the serving function of the station it names, as that
     * station's {@code /remote} route would: as the serving station's partnership with the asking one, the
     * body and the answer each written as JSON and read back. The station holding the appointment can be
     * switched off, which answers as an unreachable installation does.
     */
    private static void routeOverHttp(FederationHttpClient httpClient) {
        doAnswer(call -> answer(
                        call.getArgument(1), null, call.getArgument(2), call.getArgument(3), call.getArgument(4)))
                .when(httpClient)
                .get(anyString(), any(FederationRequest.class), any(UUID.class), anyInt(), any(Class.class));
        doAnswer(call -> answerList(call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)))
                .when(httpClient)
                .getList(anyString(), any(FederationRequest.class), any(UUID.class), anyInt(), any(Class.class));
        doAnswer(call -> answer(
                        call.getArgument(1),
                        call.getArgument(2),
                        call.getArgument(3),
                        call.getArgument(4),
                        call.getArgument(5)))
                .when(httpClient)
                .post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt(), any(Class.class));
        doAnswer(call -> taken(call.getArgument(1), call.getArgument(2), call.getArgument(3), call.getArgument(4)))
                .when(httpClient)
                .post(anyString(), any(FederationRequest.class), any(), any(UUID.class), anyInt());
    }

    private static Object answer(FederationRequest request, Object body, UUID target, int asking, Class<?> type) {
        Object answered = serve(request, body, target, asking);
        return answered == null ? null : WIRE.readValue(API.writeValueAsString(answered), type);
    }

    private static List<?> answerList(FederationRequest request, UUID target, int asking, Class<?> type) {
        try {
            Object answered = serve(request, null, target, asking);
            return WIRE.readValue(
                    API.writeValueAsString(answered), WIRE.getTypeFactory().constructCollectionType(List.class, type));
        } catch (RefusalResponse e) {
            return List.of();
        }
    }

    private static boolean taken(FederationRequest request, Object body, UUID target, int asking) {
        try {
            serve(request, body, target, asking);
            return true;
        } catch (RefusalResponse e) {
            return false;
        }
    }

    private static Object serve(FederationRequest request, Object body, UUID target, int asking) {
        if (ORGANISER_DOWN.get() && target.equals(organiser.station().uid())) {
            throw FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER.raise();
        }
        var askingUid = stationRepo.requireUid(asking);
        var servingStation = stationRepo.findHereByUid(target).orElseThrow();
        var servingRow = federationRepo
                .findPartnerByStationAndRemoteUid(servingStation.id(), askingUid)
                .orElseThrow();
        var endpoint = request.endpoint();
        Object read = body == null || endpoint.requestType() == Void.class
                ? null
                : API.readValue(API.writeValueAsString(body), endpoint.requestType());
        FederationHandler<Object, Object> handler = endpoints.handlerFor(endpoint);
        return handler.serve(new ServingPartner(servingRow, askingUid), PathParams.of(request), read);
    }
}
