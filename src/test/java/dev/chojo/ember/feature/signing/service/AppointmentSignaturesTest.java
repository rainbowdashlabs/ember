/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.content.route.BlockRowRequest;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureField;
import dev.chojo.ember.feature.generator.entity.RequirementSignatureState;
import dev.chojo.ember.feature.generator.entity.SignatureRole;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService.RequiredDocumentStatus;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.generator.service.MemberNeutralTemplates;
import dev.chojo.ember.feature.generator.service.PaperSubmissionService;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.entity.FieldState;
import dev.chojo.ember.feature.signing.entity.PendingSignature;
import dev.chojo.ember.feature.signing.entity.RequestState;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.handler.RegistrationSignaturesHandler;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import io.javalin.http.UploadedFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.row;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.signature;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static EventRequirementService requirements;
    private static AppointmentDocumentService appointments;
    private static PaperSubmissionService scans;
    private static EventRegistrationService registrations;
    private static SignatureRequestService requests;
    private static SignatureFieldService fields;
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
                requestRepo, evidenceRepo, requests, guards, guardianPolicy, memberNameResolver, notices);

        var requirementRepo = new EventRequirementRepository();
        var submissions = new PaperSubmissionRepository();
        requirements = new EventRequirementService(
                requirementRepo,
                wiring.templates(),
                new MemberNeutralTemplates(wiring.templates()),
                new EventFederationRepository());
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
                new RequirementSignatureStates(requestRepo, requests));
        scans = new PaperSubmissionService(
                submissions,
                appointments,
                wiring.templates(),
                wiring.documents(),
                new DocumentCatalogService(memberDocumentRepo, wiring.documents(), new SignatureSummaries(requestRepo)),
                memberNameResolver,
                newNotifier(),
                new ScanSignatures(requestRepo, fields));
        var signatures = new AppointmentSignatures(
                eventRepo, eventRegistrationRepo, appointments, requirementRepo, submissions, requestRepo, requests);
        registrations = new EventRegistrationService(
                eventRegistrationRepo,
                new EventRegistrationFieldRepository(),
                eventRepo,
                new DomainEventBus(Set.of(new RegistrationSignaturesHandler(() -> signatures))),
                memberNameResolver);
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
}
