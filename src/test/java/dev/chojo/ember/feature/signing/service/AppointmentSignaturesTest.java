/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository;
import dev.chojo.ember.feature.events.service.EventGroupPartDates;
import dev.chojo.ember.feature.events.service.EventGroupQuestionService;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureField;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService.ParticipantDocuments;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService.RequiredDocumentStatus;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.generator.service.MemberNeutralTemplates;
import dev.chojo.ember.feature.generator.service.PaperSubmissionService;
import dev.chojo.ember.feature.generator.service.ParticipantCopyService;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.signing.entity.AppointmentRequest;
import dev.chojo.ember.feature.signing.entity.CompletedSigning;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.OpenSignature;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureLevel;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.Signer;
import dev.chojo.ember.feature.signing.entity.SignerCapacity;
import dev.chojo.ember.feature.signing.entity.SigningAct;
import dev.chojo.ember.feature.signing.entity.SigningCircumstances;
import dev.chojo.ember.feature.signing.entity.SigningEvidence;
import dev.chojo.ember.feature.signing.handler.RegistrationSignaturesHandler;
import dev.chojo.ember.feature.signing.repository.IssuerSignatureRepository;
import dev.chojo.ember.feature.signing.repository.PartnerAgreementRepository;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.util.TestEventPlaces;
import io.javalin.http.UploadedFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Registering for an appointment whose documents to bring ask for signatures: the copy is generated and its
 * signatures are asked for whichever way the place is taken, nothing is asked twice, the participant's
 * status shows each field signed, open, confirmed on paper or waived, a confirmed scan settles the open
 * fields on paper, and giving the place up withdraws what is still open and stops its reminders.
 */
class AppointmentSignaturesTest extends GeneratorTestBase {
    private static final Instant START = Instant.parse("2026-10-10T07:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-10-10");
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();

    private static Wiring wiring;
    private static AppointmentSignatures signatures;
    private static EventRequirementService requirements;
    private static AppointmentDocumentService appointments;
    private static PaperSubmissionService scans;
    private static ParticipantCopyService copies;
    private static EventRegistrationService registrations;
    private static SignatureRequestService requests;
    private static SignatureFieldService fields;
    private static AgreementAttendance attendance;
    private static AgreementOffers offers;
    private static AgreementSigners signers;
    private static SignatureWithdrawals withdrawals;
    private static StationMember manager;
    private static StationMember guardian;
    private static StationMember child;
    private static StationMember adult;
    private static int loginPermission;

    private StationEvent camp;
    private int consent;

    @BeforeAll
    static void setup() {
        wiring = wire(stationRepo.create("Appointment Signing Station"));
        loginPermission = stationMemberRepo
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow()
                .id();
        manager = member("Maria", "Leitung", true);
        guardian = member("Gabi", "Gerber", true);
        child = member("Kim", "Gerber", false);
        adult = member("Alex", "Albers", true);
        stationMemberRepo.addManager(guardian.id(), child.id(), manager.id());

        var guardianPolicy = new GuardianPolicy(stationMemberRepo);
        var guards = new SigningGuards(
                new DocumentAccessService(memberDocumentRepo, wiring.documents(), guardianPolicy),
                memberDocumentRepo,
                guardianPolicy);
        var notices = TestNotices.notices(
                newNotifier(), emailQueueRepo, stationRepo, stationMemberRepo, accountRepo, memberDocumentRepo);
        var evidenceRepo = new SigningEvidenceRepository();
        requests = new SignatureRequestService(
                requestRepo,
                evidenceRepo,
                wiring.log(),
                memberDocumentRepo,
                wiring.documents(),
                stationMemberRepo,
                memberNameResolver,
                guardianPolicy,
                new SignerResolver(stationMemberRepo, memberNameResolver, memberPermissionResolver),
                guards,
                notices,
                new TemplateDocumentStatements(wiring.generator()));
        fields = new SignatureFieldService(
                requestRepo,
                evidenceRepo,
                requests,
                guards,
                guardianPolicy,
                memberNameResolver,
                notices,
                completed -> attendance.completed(completed),
                new WitnessReach(stationMemberRepo, restrictionService));

        var requirementRepo = new EventRequirementRepository();
        var submissions = new PaperSubmissionRepository();
        requirements = new EventRequirementService(
                requirementRepo,
                wiring.templates(),
                new MemberNeutralTemplates(wiring.templates()),
                new EventFederationRepository(),
                new DomainEventBus(Set.of()),
                new EventGroupRepository(),
                new EventGroupPartnerRepository());
        appointments = new AppointmentDocumentService(
                requirementRepo,
                submissions,
                wiring.templates(),
                wiring.generator(),
                wiring.generation(),
                guardianPolicy,
                eventRegistrationRepo,
                eventFieldRepo,
                memberNameResolver,
                wiring.issuers(),
                new EventRestrictionService(eventRepo, restrictionService),
                new RequirementSignatureStates(requestRepo, requests, new WithdrawalRights(guardianPolicy)),
                stationMemberRepo,
                occurrenceCalendar,
                new EventGroupPartDates(new EventGroupRepository(), eventRepo, occurrenceCalendar));
        var catalog =
                new DocumentCatalogService(memberDocumentRepo, wiring.documents(), new SignatureSummaries(requestRepo));
        copies = new ParticipantCopyService(appointments, requirementRepo, catalog, wiring.documents());
        scans = new PaperSubmissionService(
                submissions,
                appointments,
                wiring.templates(),
                wiring.documents(),
                catalog,
                memberNameResolver,
                newNotifier(),
                new ScanSignatures(requestRepo, fields, notices));
        signatures = new AppointmentSignatures(
                eventRepo,
                eventRegistrationRepo,
                appointments,
                requirementRepo,
                submissions,
                requestRepo,
                requests,
                new DocumentGenerationRepository(),
                new IssuerSignatureRepository(),
                mock(IssuedLetterSigner.class),
                new EventGroupPartDates(new EventGroupRepository(), eventRepo, occurrenceCalendar));
        registrations = new EventRegistrationService(
                eventRegistrationRepo,
                new EventRegistrationFieldRepository(),
                eventRepo,
                new DomainEventBus(Set.of(new RegistrationSignaturesHandler(() -> signatures))),
                memberNameResolver,
                TestEventPlaces.of(eventRegistrationRepo));
        attendance = new AgreementAttendance(requestRepo, eventRepo, eventRegistrationRepo, registrations);
        var rights = new WithdrawalRights(guardianPolicy);
        offers = new AgreementOffers(
                appointments,
                signatures,
                guardianPolicy,
                new EventRestrictionService(eventRepo, restrictionService),
                new RequirementSignatureStates(requestRepo, requests, rights));
        signers = new AgreementSigners(
                requestRepo,
                requirementRepo,
                eventRegistrationRepo,
                memberNameResolver,
                new PartnerAgreementRepository(),
                new EventFederationRepository());
        withdrawals = new SignatureWithdrawals(
                requestRepo,
                requests,
                rights,
                memberNameResolver,
                mock(SigningStateSealer.class),
                notices,
                eventRepo,
                eventRegistrationRepo,
                signatures,
                mock(PartnerSignatures.class));
        stationMemberRepo.grantPermission(
                manager.id(),
                stationMemberRepo
                        .findPermissionByName(StationPermission.EVENT_MANAGER)
                        .orElseThrow()
                        .id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(wiring.station().id());
    }

    @BeforeEach
    void appointment() {
        camp = eventRepo.create(
                wiring.station().id(),
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
        consent = template(
                "Einverständnis " + NAMES.incrementAndGet(),
                row(text("Ich darf ins Zeltlager.")),
                row(
                        signature(SignatureRole.PARTICIPANT, "Teilnehmende Person"),
                        signature(SignatureRole.GUARDIAN_1, "Erziehungsberechtigte Person")));
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
    }

    /**
     * A guardian registering the child asks for the copy's signatures at once: the copy is generated for the
     * child, generated by the guardian and filed for the date, its request asks the child and the guardian,
     * names nobody as asking, and both fields are the guardian's to sign now, the child's through the
     * guardian's account. The open fields are what the guardian owes.
     */
    @Test
    void registeringAsksForTheSignaturesOfTheDocumentToBring() {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());

        var copy = onlyCopy(child);
        assertEquals(guardian.id(), copy.generatedBy());
        assertEquals(camp.id(), copy.eventId());
        assertEquals(DAY, copy.eventDate());
        var request = onlyRequest(child);
        assertEquals(RequestState.OPEN, request.state());
        assertNull(request.createdBy(), "the appointment asks, nobody in particular");
        assertEquals(copy.id(), request.generationId());
        assertEquals(
                List.of("participant", "guardian1"),
                requestRepo.fieldsOf(request.id()).stream()
                        .map(RequestedSignature::fieldName)
                        .toList());

        var signature = statusFor(guardian, child).signature();
        assertNotNull(signature);
        assertEquals(request.uid(), signature.requestUid());
        assertEquals(RequirementSignatureState.OPEN, signature.state());
        assertTrue(signature.fields().stream().allMatch(RequirementSignatureField::yours));
        assertEquals(2, owedBy(guardian, request).size());
    }

    /** An adult registering themselves signs their own field; a stranger reading the status signs nothing. */
    @Test
    void anAdultSignsTheirOwnFieldAndNobodyElse() {
        consent = template(
                "Selbsterklärung " + NAMES.incrementAndGet(),
                row(signature(SignatureRole.PARTICIPANT, "Teilnehmende Person")));
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));

        registrations.register(camp.id(), adult.id(), DAY, false, null);

        assertEquals(adult.id(), onlyCopy(adult).generatedBy());
        var mine = statusFor(adult, adult).signature();
        assertNotNull(mine);
        assertTrue(mine.fields().getFirst().yours());
        var seenByManager = appointments
                .documentsToBring(stationSession(manager, StationPermission.EVENT_REGISTRATION), camp, DAY, true)
                .participants()
                .stream()
                .filter(participant -> participant.memberId() == adult.id())
                .findFirst()
                .orElseThrow()
                .documents()
                .getFirst()
                .signature();
        assertNotNull(seenByManager);
        assertFalse(seenByManager.fields().getFirst().yours());
    }

    /** A document that only asks the issuer to sign, or nobody, is not generated on registering. */
    @Test
    void aDocumentWithoutAFieldForTheMemberSideIsNotGenerated() {
        int plain = template("Packliste " + NAMES.incrementAndGet(), row(text("Schlafsack")));
        int issued = template(
                "Bestätigung " + NAMES.incrementAndGet(), row(signature(SignatureRole.ISSUER, "Jugendwartin")));
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(plain, issued));

        registrations.register(camp.id(), adult.id(), DAY, true, null);

        assertTrue(copiesOf(adult).isEmpty());
        assertTrue(requestRepo
                .latestForAppointment(camp.id(), DAY, List.of(adult.id()))
                .isEmpty());
    }

    /** Accepting a pending registration asks nothing again, and neither does a manager writing the status. */
    @Test
    void takingThePlaceAgainAsksNothingTwice() {
        var registration = registrations.register(camp.id(), child.id(), DAY, false, guardian.id());
        assertEquals(RegistrationStatus.PENDING, registration.status());

        registrations.updateStatus(registration.id(), RegistrationStatus.ACCEPTED);

        assertEquals(1, copiesOf(child).size());
        assertEquals(
                1, requestRepo.liveForAppointment(camp.id(), DAY, child.id()).size());
    }

    /**
     * Withdrawing the registration withdraws the open request: its fields are withdrawn, nothing is owed or
     * reminded of any more. Taking the withdrawal back asks again on the same copy.
     */
    @Test
    void withdrawingWithdrawsTheOpenRequestAndTakingItBackAsksAgain() {
        var registration = registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var first = onlyRequest(child);

        registrations.withdraw(registration.id());

        var withdrawn = requestRepo.findById(first.id()).orElseThrow();
        assertEquals(RequestState.WITHDRAWN, withdrawn.state());
        assertTrue(requestRepo.fieldsOf(first.id()).stream().allMatch(field -> field.state() == FieldState.WITHDRAWN));
        assertTrue(owedBy(guardian, first).isEmpty());
        assertTrue(
                requestRepo.dueForReminder(Instant.now().plus(Duration.ofDays(60)), Duration.ofDays(7), 3, 500).stream()
                        .noneMatch(due -> due.pending().requestUid().equals(first.uid())));

        registrations.undoWithdrawal(registration.id());

        var again = requestRepo.liveForAppointment(camp.id(), DAY, child.id());
        assertEquals(1, again.size());
        assertEquals(first.generationId(), again.getFirst().request().generationId(), "the same copy is asked on");
        assertEquals(1, copiesOf(child).size());
    }

    /** What was signed stays when the place is given up; only the open field is withdrawn. */
    @Test
    void aSignedFieldStaysWhenThePlaceIsGivenUp() {
        var registration = registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var request = onlyRequest(child);
        var participant = field(request, "participant");
        requestRepo.settle(participant.id(), FieldState.SIGNED, child.id(), "Kim Gerber");

        registrations.refuse(registration.id());

        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
        assertEquals(FieldState.SIGNED, field(request, "participant").state());
        assertEquals(FieldState.WITHDRAWN, field(request, "guardian1").state());
    }

    /**
     * A manager of the registrations, without any right to member documents, confirms the scan the guardian
     * handed in: every open field is settled as confirmed on paper by the manager, the request completes and
     * the status shows the copy confirmed on paper.
     */
    @Test
    void confirmingTheScanSettlesTheOpenFieldsOnPaper() throws IOException {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var request = onlyRequest(child);
        var submission = scans.submit(stationSession(guardian), camp, DAY, consent, child.id(), null, scan());
        assertEquals(
                RequirementSignatureState.OPEN,
                statusFor(guardian, child).signature().state());

        scans.confirm(stationSession(manager, StationPermission.EVENT_REGISTRATION), camp, submission.id());

        assertTrue(owedBy(guardian, request).isEmpty());

        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
        for (var settled : requestRepo.fieldsOf(request.id())) {
            assertEquals(FieldState.PAPER_CONFIRMED, settled.state());
            assertEquals(manager.id(), settled.settledBy());
        }
        var status = statusFor(guardian, child);
        assertEquals(PaperState.CONFIRMED, status.paper().state());
        assertEquals(
                RequirementSignatureState.PAPER_CONFIRMED, status.signature().state());
        assertTrue(status.signature().fields().stream().noneMatch(RequirementSignatureField::yours));
    }

    /**
     * While the guardian's scan waits for a manager, the copy's open fields are asked of nobody: they are not
     * owed, not offered to sign in one go, not reminded of, the requests already out for them are taken back,
     * and the status shows the scan waiting with nothing to sign. Nothing about the fields is stored for the
     * wait. Turning the scan down asks for them again and the reminders resume.
     */
    @Test
    void aWaitingScanAsksForNothingUntilItIsTurnedDown() throws IOException {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var request = onlyRequest(child);
        assertEquals(2, owedBy(guardian, request).size());
        int requested = countOf(guardian, "SIGNATURE_REQUESTED");

        var submission = scans.submit(stationSession(guardian), camp, DAY, consent, child.id(), null, scan());

        assertTrue(owedBy(guardian, request).isEmpty());
        assertTrue(offeredInOneGo(guardian, request).isEmpty());
        assertFalse(dueForReminder(request));
        assertEquals(requested - 2, countOf(guardian, "SIGNATURE_REQUESTED"), "the requests are taken back");
        var waiting = statusFor(guardian, child);
        assertEquals(PaperState.SUBMITTED, waiting.paper().state());
        assertEquals(RequirementSignatureState.OPEN, waiting.signature().state());
        assertTrue(waiting.signature().fields().stream().noneMatch(RequirementSignatureField::yours));
        assertTrue(requestRepo.fieldsOf(request.id()).stream().allMatch(field -> field.state() == FieldState.OPEN));

        scans.reject(
                stationSession(manager, StationPermission.EVENT_REGISTRATION), camp, submission.id(), "Unleserlich");

        assertEquals(2, owedBy(guardian, request).size());
        assertEquals(2, offeredInOneGo(guardian, request).size());
        assertTrue(dueForReminder(request));
        assertTrue(statusFor(guardian, child).signature().fields().stream().allMatch(RequirementSignatureField::yours));
        assertTrue(notificationsOf(guardian).contains("DOCUMENT_SCAN_REJECTED"));
    }

    /**
     * A scan a manager hands in counts as confirmed at once and settles the fields the same way; a field
     * signed before keeps its signature.
     */
    @Test
    void aManagersScanSettlesOnlyWhatIsStillOpen() throws IOException {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var request = onlyRequest(child);
        requestRepo.settle(field(request, "guardian1").id(), FieldState.SIGNED, guardian.id(), "Gabi Gerber");

        scans.submit(
                stationSession(manager, StationPermission.EVENT_REGISTRATION),
                camp,
                DAY,
                consent,
                child.id(),
                null,
                scan());

        assertEquals(FieldState.PAPER_CONFIRMED, field(request, "participant").state());
        assertEquals(FieldState.SIGNED, field(request, "guardian1").state());
        assertEquals(
                RequirementSignatureState.PAPER_CONFIRMED,
                statusFor(guardian, child).signature().state());
    }

    /**
     * A document the appointment asks for only after the member registered, whose signed paper copy a manager
     * handed in, is not asked for when the place is taken again.
     */
    @Test
    void aConfirmedPaperCopyIsNotAskedForAgain() throws IOException {
        requirements.setForEvent(wiring.owner(), camp.id(), List.of());
        var registration = registrations.register(camp.id(), child.id(), DAY, false, guardian.id());
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        scans.submit(
                stationSession(manager, StationPermission.EVENT_REGISTRATION),
                camp,
                DAY,
                consent,
                child.id(),
                null,
                scan());

        registrations.updateStatus(registration.id(), RegistrationStatus.ACCEPTED);

        assertTrue(requestRepo.liveForAppointment(camp.id(), DAY, child.id()).isEmpty());
        assertTrue(copiesOf(child).isEmpty());
    }

    /** A waived field shows as waived beside the open one, and the copy shows waived once all are. */
    @Test
    void waivedFieldsShowAsWaived() {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var request = onlyRequest(child);
        var editor =
                stationSession(manager, StationPermission.DOCUMENT_EDIT_MEMBER, StationPermission.DOCUMENT_READ_MEMBER);

        fields.waive(editor, request.uid(), "participant");

        var partly = statusFor(guardian, child).signature();
        assertEquals(RequirementSignatureState.OPEN, partly.state());
        assertEquals(
                RequirementSignatureState.WAIVED, partly.fields().getFirst().state());
        assertFalse(partly.fields().getFirst().yours());

        fields.waive(editor, request.uid(), "guardian1");

        assertEquals(
                RequirementSignatureState.WAIVED,
                statusFor(guardian, child).signature().state());
    }

    /**
     * An appointment without registrations offers its agreement to the guardian for the child; asking for it
     * files a copy and asks its signatures, offering it again asks nothing twice, and once both fields are
     * signed the guardian's earlier "not coming" for the child is taken back and the organiser lists the
     * child as a signer. Saying "not coming" again afterwards marks the signer as refusing.
     */
    @Test
    void anAppointmentWithoutRegistrationsOffersItsAgreementAndSigningCountsAsComing() {
        camp = appointment(false);
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        registrations.decline(camp.id(), child.id(), DAY, guardian.id());
        assertTrue(statusFor(guardian, child).agreementOffered());
        assertNull(statusFor(guardian, child).signature());

        var offered = offers.offer(stationSession(guardian), camp, DAY, consent, child.id());

        assertEquals(RequirementSignatureState.OPEN, offered.state());
        assertTrue(offered.fields().stream().allMatch(RequirementSignatureField::yours));
        assertFalse(statusFor(guardian, child).agreementOffered());
        offers.offer(stationSession(guardian), camp, DAY, consent, child.id());
        assertEquals(1, copiesOf(child).size(), "offering again asks nothing twice");
        var request = onlyRequest(child);

        signBoth(request);

        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state());
        assertEquals(RegistrationStatus.WITHDRAWN, answerOf(child).status(), "the refusal is taken back");
        var listed = signers.of(camp.id(), DAY);
        assertEquals(1, listed.size());
        assertEquals(child.id(), listed.getFirst().memberId());
        assertEquals(RequirementSignatureState.SIGNED, listed.getFirst().state());
        assertFalse(listed.getFirst().refused());

        registrations.decline(camp.id(), child.id(), DAY, guardian.id());

        assertTrue(signers.of(camp.id(), DAY).getFirst().refused());
        assertEquals(
                RequestState.COMPLETE,
                requestRepo.findById(request.id()).orElseThrow().state(),
                "a signed agreement stays when somebody says they will not come");
    }

    /**
     * The agreement is only offered where it is signed this way: not on an appointment that takes
     * registrations, not for a member the reader does not act for, and not for a document the appointment
     * does not ask for.
     */
    @Test
    void anAgreementIsOfferedOnlyWhereItIsSignedOnThePage() {
        assertRefused(
                EventRefusal.AGREEMENT_SIGNED_ON_REGISTERING,
                () -> offers.offer(stationSession(guardian), camp, DAY, consent, child.id()));

        camp = appointment(false);
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        assertRefused(
                EventRefusal.AGREEMENT_NOT_FOR_MEMBER,
                () -> offers.offer(stationSession(adult), camp, DAY, consent, child.id()));
        int unasked = template("Fremd " + NAMES.incrementAndGet(), row(signature(SignatureRole.PARTICIPANT, "Name")));
        assertRefused(
                EventRefusal.AGREEMENT_NOTHING_TO_SIGN,
                () -> offers.offer(stationSession(guardian), camp, DAY, unasked, child.id()));
        assertTrue(copiesOf(child).isEmpty());
    }

    /**
     * Withdrawing a signed agreement of a registration revokes its request, flags the registration and asks
     * for the agreement again on a fresh copy, so the requirement is open again; signing it anew takes the
     * flag off. Whoever runs the appointment is told.
     */
    @Test
    void withdrawingReopensTheRequirementAndFlagsTheRegistration() {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var signed = onlyRequest(child);
        signBoth(signed);
        assertNull(answerOf(child).agreementWithdrawnAt());
        assertTrue(statusFor(guardian, child).signature().withdrawable());

        var withdrawal = withdrawals.requireOwnedThenWithdraw(
                stationSession(guardian),
                signed.uid(),
                "  Doch krank  ",
                new SigningCircumstances("203.0.113.57", "Test Browser"));

        assertEquals("Doch krank", withdrawal.reason());
        assertEquals(SignerCapacity.GUARDIAN, withdrawal.capacity());
        assertEquals("203.0.113.0", withdrawal.truncatedIp());
        assertEquals(
                RequestState.REVOKED,
                requestRepo.findById(signed.id()).orElseThrow().state());
        assertNotNull(answerOf(child).agreementWithdrawnAt(), "the registration is flagged");
        assertEquals(answerOf(child).agreementWithdrawnAt(), overviewOf(child).agreementWithdrawnAt());
        assertNull(appointments
                .documentsToBring(stationSession(guardian), camp, DAY, false)
                .own()
                .getFirst()
                .agreementWithdrawnAt());
        var again = onlyRequest(child);
        assertNotEquals(signed.generationId(), again.generationId(), "asked anew on a fresh copy");
        assertEquals(RequestState.OPEN, again.state());
        assertEquals(
                RequirementSignatureState.OPEN,
                statusFor(guardian, child).signature().state());
        assertEquals(2, owedBy(guardian, again).size());
        assertTrue(notificationsOf(manager).contains("SIGNATURE_WITHDRAWN"));

        signBoth(again);

        assertNull(answerOf(child).agreementWithdrawnAt(), "signed anew, the flag is off");
        assertNull(overviewOf(child).agreementWithdrawnAt());
    }

    /**
     * Whoever manages the registrations sees the fields of a child without a login and without guardians as
     * fields nobody can sign, and those of a child with a guardian as signable. They download a
     * participant's copy without the right to read member documents, and get none for a member who takes
     * no part.
     */
    @Test
    void aManagerSeesFieldsNobodyCanSignAndDownloadsTheCopy() {
        var orphan = member("Ole", "Allein", false);
        registrations.register(camp.id(), orphan.id(), DAY, true, manager.id());
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());

        assertTrue(overviewOf(orphan).documents().getFirst().signature().fields().stream()
                .allMatch(RequirementSignatureField::nobodyCanSign));
        assertTrue(overviewOf(child).documents().getFirst().signature().fields().stream()
                .noneMatch(RequirementSignatureField::nobodyCanSign));

        var managing = stationSession(manager, StationPermission.EVENT_REGISTRATION);
        var copy = copies.copyOf(managing, camp, DAY, consent, child.id());
        assertEquals(onlyCopy(child).documentId(), copy.document().id());
        assertTrue(copy.data().length > 0);
        assertRefused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                () -> copies.copyOf(managing, camp, DAY, consent, adult.id()));
    }

    /** Where a participant stands with the appointment's documents, as whoever manages the registrations sees it. */
    private ParticipantDocuments overviewOf(StationMember participant) {
        var managing = stationSession(manager, StationPermission.EVENT_REGISTRATION);
        return appointments.documentsToBring(managing, camp, DAY, true).participants().stream()
                .filter(seen -> seen.memberId() == participant.id())
                .findFirst()
                .orElseThrow();
    }

    /**
     * On an appointment without registrations a withdrawn agreement shows as revoked, is offered again, and
     * the organiser's list shows when it was withdrawn. Withdrawing it twice is refused, and so is a
     * stranger's withdrawal.
     */
    @Test
    void aWithdrawnAgreementWithoutRegistrationsIsOfferedAgain() {
        camp = appointment(false);
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        offers.offer(stationSession(guardian), camp, DAY, consent, child.id());
        var request = onlyRequest(child);
        signBoth(request);
        var circumstances = new SigningCircumstances(null, null);
        assertRefused(
                DocumentRefusal.SIGNATURE_WITHDRAWAL_NOT_YOURS,
                () -> withdrawals.requireOwnedThenWithdraw(stationSession(adult), request.uid(), null, circumstances));

        withdrawals.requireOwnedThenWithdraw(stationSession(guardian), request.uid(), null, circumstances);

        var status = statusFor(guardian, child);
        assertEquals(RequirementSignatureState.REVOKED, status.signature().state());
        assertNotNull(status.signature().withdrawnAt());
        assertFalse(status.signature().withdrawable());
        assertTrue(status.agreementOffered());
        var listed = signers.of(camp.id(), DAY).getFirst();
        assertEquals(RequirementSignatureState.REVOKED, listed.state());
        assertNotNull(listed.withdrawnAt());
        assertRefused(
                DocumentRefusal.SIGNATURE_WITHDRAWAL_ENDED,
                () -> withdrawals.requireOwnedThenWithdraw(
                        stationSession(guardian), request.uid(), null, circumstances));
    }

    /** An agreement nobody signed yet has nothing to withdraw, and a reason too long is refused. */
    @Test
    void anUnsignedAgreementAndAnOverlongReasonAreRefused() {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var request = onlyRequest(child);
        var circumstances = new SigningCircumstances(null, null);
        assertRefused(
                DocumentRefusal.SIGNATURE_WITHDRAWAL_REASON_TOO_LONG,
                () -> withdrawals.requireOwnedThenWithdraw(
                        stationSession(guardian), request.uid(), "x".repeat(501), circumstances));

        assertRefused(
                DocumentRefusal.SIGNATURE_WITHDRAWAL_NOTHING_SIGNED,
                () -> withdrawals.requireOwnedThenWithdraw(
                        stationSession(guardian), request.uid(), null, circumstances));
    }

    /**
     * An agreement confirmed on paper is withdrawn like one signed online: the withdrawal is recorded, the
     * request turns revoked, the registration is flagged and the agreement is asked for anew. Nothing online
     * was sealed, so the withdrawal stands recorded without a sealed version. The confirmed scan is withdrawn
     * with it, so it no longer settles the document and a new scan can be handed in.
     */
    @Test
    void aPaperConfirmedAgreementIsWithdrawnAndAskedForAnew() throws IOException {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var confirmed = onlyRequest(child);
        scans.submit(
                stationSession(manager, StationPermission.EVENT_REGISTRATION),
                camp,
                DAY,
                consent,
                child.id(),
                null,
                scan());
        assertTrue(statusFor(guardian, child).signature().withdrawable());

        var withdrawal = withdrawals.requireOwnedThenWithdraw(
                stationSession(guardian), confirmed.uid(), "Doch nicht", new SigningCircumstances(null, null));

        assertEquals("Doch nicht", withdrawal.reason());
        assertFalse(
                new SigningEvidenceRepository()
                        .requestsToSeal(Instant.now().plusSeconds(60), 500)
                        .contains(confirmed.id()),
                "nothing online to seal the withdrawal into");
        assertEquals(
                RequestState.REVOKED,
                requestRepo.findById(confirmed.id()).orElseThrow().state());
        assertNotNull(answerOf(child).agreementWithdrawnAt(), "the registration is flagged");
        var again = onlyRequest(child);
        assertNotEquals(confirmed.generationId(), again.generationId(), "asked anew on a fresh copy");
        assertEquals(2, owedBy(guardian, again).size());
        var status = statusFor(guardian, child);
        assertEquals(PaperState.WITHDRAWN, status.paper().state());
        assertEquals(RequirementSignatureState.OPEN, status.signature().state());
        assertTrue(notificationsOf(manager).contains("SIGNATURE_WITHDRAWN"));

        var handedIn = scans.submit(stationSession(guardian), camp, DAY, consent, child.id(), null, scan());

        assertEquals(PaperState.SUBMITTED, handedIn.state());
    }

    /**
     * A scan that waits is taken back by the guardian: the submission and the scan it filed are gone and the
     * fields are asked for again. A confirmed scan cannot be taken back that way.
     */
    @Test
    void aWaitingScanIsTakenBackAndTheFieldsAreAskedForAgain() throws IOException {
        registrations.register(camp.id(), child.id(), DAY, true, guardian.id());
        var request = onlyRequest(child);
        var waiting = scans.submit(stationSession(guardian), camp, DAY, consent, child.id(), null, scan());
        assertTrue(owedBy(guardian, request).isEmpty());

        scans.withdraw(stationSession(guardian), camp, waiting.id());

        assertNull(statusFor(guardian, child).paper());
        assertTrue(memberDocumentRepo.findById(waiting.documentId()).isEmpty(), "the scan is deleted");
        assertEquals(2, owedBy(guardian, request).size());
        var confirmed = scans.submit(
                stationSession(manager, StationPermission.EVENT_REGISTRATION),
                camp,
                DAY,
                consent,
                child.id(),
                null,
                scan());
        assertRefused(
                DocumentRefusal.DOCUMENT_SCAN_NOT_WAITING,
                () -> scans.withdraw(stationSession(guardian), camp, confirmed.id()));
    }

    private static StationEvent appointment(boolean takesRegistrations) {
        return eventRepo.create(
                wiring.station().id(),
                "Zeltlager " + NAMES.incrementAndGet(),
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(8)),
                null,
                takesRegistrations,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    /** The guardian signs both fields of the child's copy: the child's through the guardian's account. */
    private static void signBoth(SignatureRequest request) {
        var session = stationSession(guardian);
        fields.record(
                session, act(request, "participant", Signer.memberThroughAccount(guardian.accountId(), child.id())));
        fields.record(session, act(request, "guardian1", Signer.guardian(guardian.accountId(), child.id())));
    }

    private static CompletedSigning act(SignatureRequest request, String fieldName, Signer signer) {
        var field = field(request, fieldName);
        var act = new SigningAct(
                request.uid(),
                signer,
                memberNameResolver.official(guardian.id()),
                memberNameResolver.official(child.id()),
                fieldName,
                field.statement(),
                HexFormat.of().parseHex(request.contentSha256()),
                List.of(),
                new byte[32],
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                "203.0.113.0",
                "Test Browser");
        return new CompletedSigning(SignatureLevel.SIMPLE, new SigningEvidence.TotpUnbound(act));
    }

    private EventRegistration answerOf(StationMember member) {
        return eventRegistrationRepo.findByEventAndDate(camp.id(), DAY).stream()
                .filter(registration -> registration.memberId() == member.id())
                .findFirst()
                .orElseThrow();
    }

    private static String notificationsOf(StationMember member) {
        return query("SELECT string_agg(type, ',') AS types FROM notification WHERE member_id = :member_id;")
                .single(call().bind("member_id", member.id()))
                .map(row -> String.valueOf(row.getString("types")))
                .first()
                .orElse("");
    }

    private static int countOf(StationMember member, String type) {
        return query("SELECT count(*) AS n FROM notification WHERE member_id = :member_id AND type = :type;")
                .single(call().bind("member_id", member.id()).bind("type", type))
                .map(row -> row.getInt("n"))
                .first()
                .orElse(0);
    }

    /** The fields of the request the signing screen offers the member to sign in one go. */
    private static List<OpenSignature> offeredInOneGo(StationMember member, SignatureRequest request) {
        return requests.openFor(stationSession(member)).stream()
                .filter(open -> open.pending().requestUid().equals(request.uid()))
                .toList();
    }

    private static boolean dueForReminder(SignatureRequest request) {
        return requestRepo.dueForReminder(Instant.now().plus(Duration.ofDays(60)), Duration.ofDays(7), 3, 500).stream()
                .anyMatch(due -> due.pending().requestUid().equals(request.uid()));
    }

    private static StationMember member(String first, String last, boolean login) {
        var account = accountRepo.create("appointment-signing-" + NAMES.incrementAndGet() + "@test.com", first, last);
        var created = stationMemberRepo.create(wiring.station().id(), account.id());
        if (login) stationMemberRepo.grantPermission(created.id(), loginPermission);
        return created;
    }

    private static int template(String name, BlockRowRequest... rows) {
        return wiring.templates()
                .create(
                        wiring.owner(),
                        letter(name).body(List.of(rows)).forAppointments(true).build(),
                        manager.id())
                .id();
    }

    private static UploadedFile scan() throws IOException {
        return TestUploads.of("scan.pdf", "application/pdf", TestPdfs.plain(1));
    }

    private RequiredDocumentStatus statusFor(StationMember reader, StationMember participant) {
        StationSession session = stationSession(reader);
        return appointments.documentsToBring(session, camp, DAY, false).own().stream()
                .filter(own -> own.memberId() == participant.id())
                .findFirst()
                .orElseThrow()
                .documents()
                .stream()
                .filter(document -> document.templateId() == consent)
                .findFirst()
                .orElseThrow();
    }

    private List<DocumentGeneration> copiesOf(StationMember participant) {
        return wiring.log().forStation(wiring.station().id(), 500, 0).stream()
                .filter(entry -> Integer.valueOf(participant.id()).equals(entry.memberId()))
                .flatMap(entry -> wiring.log().findById(entry.id()).stream())
                .filter(generation -> Integer.valueOf(camp.id()).equals(generation.eventId()))
                .toList();
    }

    private DocumentGeneration onlyCopy(StationMember participant) {
        var copies = copiesOf(participant);
        assertEquals(1, copies.size());
        return copies.getFirst();
    }

    private SignatureRequest onlyRequest(StationMember participant) {
        var live = requestRepo.liveForAppointment(camp.id(), DAY, participant.id());
        assertEquals(1, live.size());
        return live.getFirst().request();
    }

    /** The open fields of the request the member is asked to sign, as the list of what they owe has them. */
    private static List<PendingSignature> owedBy(StationMember member, SignatureRequest request) {
        return requests.pendingFor(wiring.station().id(), member.id()).stream()
                .filter(pending -> pending.requestUid().equals(request.uid()))
                .toList();
    }

    private static RequestedSignature field(SignatureRequest request, String name) {
        return requestRepo.fieldsOf(request.id()).stream()
                .filter(field -> field.fieldName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** The day before the first part of an event group, which lies ahead so its parts can be given up. */
    private static final Instant GROUP_START = Instant.parse("2027-03-06T07:00:00Z");

    private static final LocalDate GROUP_DAY = LocalDate.parse("2027-03-06");

    /** Two parts of a new event group on the two days after {@link #GROUP_START}, asking for the consent. */
    private record GroupParts(int eventGroupId, StationEvent first, StationEvent second) {}

    private GroupParts eventGroupAskingForConsent() {
        var eventGroups = newEventGroupService();
        var eventGroup = eventGroups.create(
                wiring.station().id(),
                new EventGroupDetails("Lehrgang " + NAMES.incrementAndGet(), null, null, null, false, null),
                List.of(new EventGroupSetDraft(null, "Tage", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        int setId = eventGroups.findSets(eventGroup.id()).getFirst().id();
        var first = partOn(eventGroups, eventGroup.id(), setId, 1);
        var second = partOn(eventGroups, eventGroup.id(), setId, 2);
        requirements.setForEventGroup(wiring.owner(), eventGroup.id(), List.of(consent));
        return new GroupParts(eventGroup.id(), first, second);
    }

    private static StationEvent partOn(EventGroupService eventGroups, int eventGroupId, int setId, int daysAfter) {
        var start = GROUP_START.plus(Duration.ofDays(daysAfter));
        var part = eventRepo.create(
                wiring.station().id(),
                "Tag " + daysAfter + " " + NAMES.incrementAndGet(),
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventGroups.addPart(wiring.station().id(), eventGroupId, part.id(), setId);
        return part;
    }

    /** Signs a member up for an event group through the event group, as their guardian. */
    private static EventGroupRegistrationService signUps() {
        return new EventGroupRegistrationService(
                new EventGroupRepository(),
                new EventGroupRegistrationRepository(),
                eventRegistrationRepo,
                registrations,
                new DomainEventBus(Set.of()),
                memberNameResolver,
                new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility),
                newEventGroupSignUpRules(),
                newEventGroupQueue(registrations, new DomainEventBus(Set.of())),
                new RegistrationQueueRepository());
    }

    private static void signUp(GroupParts group, StationEvent... parts) {
        signUps()
                .register(
                        wiring.station().id(),
                        group.eventGroupId(),
                        child.id(),
                        java.util.Arrays.stream(parts)
                                .map(StationEvent::id)
                                .collect(java.util.stream.Collectors.toSet()),
                        java.util.Map.of(),
                        guardian.id(),
                        false);
    }

    private static List<SignatureRequest> liveOn(StationEvent part, int daysAfter, StationMember member) {
        return requestRepo.liveForAppointment(part.id(), GROUP_DAY.plusDays(daysAfter), member.id()).stream()
                .map(AppointmentRequest::request)
                .toList();
    }

    private static RequiredDocumentStatus documentOn(
            StationEvent part, int daysAfter, StationMember reader, StationMember participant, int templateId) {
        return appointments
                .documentsToBring(stationSession(reader), part, GROUP_DAY.plusDays(daysAfter), false)
                .own()
                .stream()
                .filter(own -> own.memberId() == participant.id())
                .findFirst()
                .orElseThrow()
                .documents()
                .stream()
                .filter(document -> document.templateId() == templateId)
                .findFirst()
                .orElseThrow();
    }

    private static List<SignatureRequest> liveOnEither(GroupParts group) {
        var live = new java.util.ArrayList<>(liveOn(group.first(), 1, child));
        live.addAll(liveOn(group.second(), 2, child));
        return live;
    }

    /**
     * A document the event group asks every part for is asked once for the whole event group: signing up
     * for two parts asks for it on one of them, and the other part shows that copy's signatures as its own.
     * Signed there, it reads as signed on every part, and the event group lists the signer once.
     */
    @Test
    void anEventGroupsDocumentIsAskedOnceAndCoversEveryPart() {
        var group = eventGroupAskingForConsent();

        signUp(group, group.first(), group.second());

        var asked = liveOnEither(group);
        assertEquals(1, asked.size(), "one request for both parts");
        for (var part : List.of(group.first(), group.second())) {
            int days = part.id() == group.first().id() ? 1 : 2;
            var shown = documentOn(part, days, guardian, child, consent).signature();
            assertNotNull(shown, "every part counts the one copy");
            assertEquals(asked.getFirst().uid(), shown.requestUid());
        }

        signBoth(asked.getFirst());

        var onSecond = documentOn(group.second(), 2, guardian, child, consent).signature();
        assertNotNull(onSecond);
        assertEquals(RequirementSignatureState.SIGNED, onSecond.state());
        var listed = new EventGroupAgreementSigners(signers, new EventRequirementRepository(), partDates())
                .of(group.eventGroupId());
        assertEquals(1, listed.size(), "the signer is listed once for the event group");
        assertEquals(child.id(), listed.getFirst().memberId());
        assertEquals(RequirementSignatureState.SIGNED, listed.getFirst().state());
    }

    /**
     * Giving up the part the event group's document was asked on while keeping another hands what is still
     * open over to that other part; withdrawing from the event group asks for nothing.
     */
    @Test
    void givingUpThePartItWasAskedOnHandsTheOpenDocumentOver() {
        var group = eventGroupAskingForConsent();
        signUp(group, group.first(), group.second());
        boolean onFirst = !liveOn(group.first(), 1, child).isEmpty();
        var kept = onFirst ? group.second() : group.first();
        var given = onFirst ? group.first() : group.second();

        signUps()
                .changeParts(
                        wiring.station().id(),
                        group.eventGroupId(),
                        child.id(),
                        Set.of(kept.id()),
                        guardian.id(),
                        false);

        assertEquals(List.of(), liveOn(given, given.id() == group.first().id() ? 1 : 2, child));
        var handedOver = liveOn(kept, kept.id() == group.first().id() ? 1 : 2, child);
        assertEquals(1, handedOver.size());
        assertEquals(RequestState.OPEN, handedOver.getFirst().state());

        signUps().withdraw(wiring.station().id(), group.eventGroupId(), child.id());

        assertEquals(List.of(), liveOnEither(group));
    }

    /** A document signed on a part keeps covering the others after that part is given up. */
    @Test
    void aSignedEventGroupDocumentStaysCoveringAfterItsPartIsGivenUp() {
        var group = eventGroupAskingForConsent();
        signUp(group, group.first(), group.second());
        var signed = liveOnEither(group).getFirst();
        signBoth(signed);
        boolean onFirst = !liveOn(group.first(), 1, child).isEmpty();
        var kept = onFirst ? group.second() : group.first();
        int keptDays = kept.id() == group.first().id() ? 1 : 2;

        signUps()
                .changeParts(
                        wiring.station().id(),
                        group.eventGroupId(),
                        child.id(),
                        Set.of(kept.id()),
                        guardian.id(),
                        false);

        assertEquals(List.of(), liveOn(kept, keptDays, child), "nothing is asked again");
        var shown = documentOn(kept, keptDays, guardian, child, consent).signature();
        assertNotNull(shown);
        assertEquals(RequirementSignatureState.SIGNED, shown.state());
    }

    /** A document a part asks for on its own is asked on that part, beside the event group's. */
    @Test
    void aPartsOwnDocumentIsAskedOnThatPart() {
        var group = eventGroupAskingForConsent();
        int ownConsent = template(
                "Fahrt " + NAMES.incrementAndGet(),
                row(text("Ich fahre mit.")),
                row(signature(SignatureRole.GUARDIAN_1, "Erziehungsberechtigte Person")));
        requirements.setForEvent(wiring.owner(), group.second().id(), List.of(ownConsent));

        signUp(group, group.first(), group.second());

        var onSecond = requestRepo.liveForAppointment(group.second().id(), GROUP_DAY.plusDays(2), child.id()).stream()
                .map(AppointmentRequest::templateId)
                .toList();
        assertTrue(onSecond.contains(ownConsent), "the part's own document is asked on the part");
        assertEquals(2, liveOnEither(group).size(), "one for the event group, one for the part");
        assertEquals(
                RequirementSignatureState.OPEN,
                Objects.requireNonNull(documentOn(group.second(), 2, guardian, child, ownConsent)
                                .signature())
                        .state());
    }

    private static EventGroupPartDates partDates() {
        return new EventGroupPartDates(new EventGroupRepository(), eventRepo, occurrenceCalendar);
    }

    /** The part an event group's document was asked on, and the other one. */
    private record AskedOn(StationEvent asked, int askedDays, StationEvent other, int otherDays) {}

    private static AskedOn askedOn(GroupParts group) {
        boolean onFirst = !liveOn(group.first(), 1, child).isEmpty();
        return onFirst
                ? new AskedOn(group.first(), 1, group.second(), 2)
                : new AskedOn(group.second(), 2, group.first(), 1);
    }

    /**
     * Taking the part the event group's document was asked on out of the event group lets the open request
     * there go and asks for the document on the other part the member holds.
     */
    @Test
    void takingThePartOutOfTheEventGroupHandsTheDocumentOver() {
        var group = eventGroupAskingForConsent();
        signUp(group, group.first(), group.second());
        var on = askedOn(group);
        var asked = liveOn(on.asked(), on.askedDays(), child).getFirst();
        var eventGroups = newEventGroupService();
        eventGroups.tellOnDeparture(new EventGroupDocumentHandover(() -> signatures));

        eventGroups.removePart(
                wiring.station().id(), group.eventGroupId(), on.asked().id());

        assertNotEquals(
                RequestState.OPEN,
                requestRepo.findById(asked.id()).orElseThrow().state());
        var handedOver = liveOn(on.other(), on.otherDays(), child);
        assertEquals(1, handedOver.size());
        assertEquals(RequestState.OPEN, handedOver.getFirst().state());
    }

    /**
     * Deleting the part the event group's document was asked on lets the open request there go and asks for
     * the document on the other part the member holds.
     */
    @Test
    void deletingThePartHandsTheDocumentOver() {
        var group = eventGroupAskingForConsent();
        signUp(group, group.first(), group.second());
        var on = askedOn(group);
        var asked = liveOn(on.asked(), on.askedDays(), child).getFirst();
        var crud = newEventServices(new DomainEventBus(Set.of())).crud();
        crud.tellOnDeparture(new EventGroupDocumentHandover(() -> signatures));

        assertTrue(crud.delete(on.asked().id()));

        assertNotEquals(
                RequestState.OPEN,
                requestRepo.findById(asked.id()).orElseThrow().state());
        var handedOver = liveOn(on.other(), on.otherDays(), child);
        assertEquals(1, handedOver.size());
        assertEquals(RequestState.OPEN, handedOver.getFirst().state());
    }
}
