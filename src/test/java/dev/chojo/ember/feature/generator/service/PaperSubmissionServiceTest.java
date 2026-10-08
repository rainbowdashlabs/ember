/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import dev.chojo.ember.feature.generator.entity.RequirementStatus;
import dev.chojo.ember.feature.generator.repository.EventRequirementRepository;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService.RequiredDocumentStatus;
import dev.chojo.ember.feature.generator.service.pdf.TestPdfs;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.service.SignatureSummaries;
import io.javalin.http.UploadedFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.feature.generator.service.TemplateRequestBuilder.letter;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Scans of signed paper copies of the documents an appointment asks for: handed in by the participant
 * or their guardian without any right to upload, filed in the participant's documents, confirmed at once
 * where a manager of the registrations hands one in, otherwise confirmed or turned down by one, and
 * refused to everybody else.
 */
class PaperSubmissionServiceTest extends GeneratorTestBase {
    private static final Instant START = Instant.parse("2026-10-10T07:00:00Z");
    private static final LocalDate DAY = LocalDate.parse("2026-10-10");

    private static Wiring wiring;
    private static EventRequirementService requirements;
    private static AppointmentDocumentService appointments;
    private static PaperSubmissionService scans;
    private static Notifier notifier;
    private static ScanConfirmations confirmations;
    private static StationMember manager;
    private static StationMember lena;
    private static StationMember guardian;
    private static StationMember max;
    private static byte[] pdf;

    private static final AtomicInteger TEMPLATES = new AtomicInteger();

    private StationEvent camp;
    private int consent;
    private String consentName = "";

    @BeforeAll
    static void setup() throws IOException {
        wiring = wire("Scan Wache");
        manager = wiring.member("scan-manager@test.com", "Nora", "Fülling");
        lena = wiring.member("scan-lena@test.com", "Lena", "Schmidt");
        guardian = wiring.member("scan-guardian@test.com", "Anna", "Schmidt");
        max = wiring.member("scan-max@test.com", "Max", "Weiß");
        stationMemberRepo.addManager(guardian.id(), lena.id());
        pdf = TestPdfs.plain(1);

        var repository = new EventRequirementRepository();
        var submissions = new PaperSubmissionRepository();
        requirements = new EventRequirementService(repository, wiring.templates());
        appointments = new AppointmentDocumentService(
                repository,
                submissions,
                wiring.templates(),
                wiring.generator(),
                wiring.generation(),
                new GuardianPolicy(stationMemberRepo),
                eventRegistrationRepo,
                eventFieldRepo,
                memberNameResolver,
                wiring.issuers(),
                new EventRestrictionService(eventRepo, restrictionService),
                RequirementSignatures.NONE);
        notifier = mock(Notifier.class);
        confirmations = mock(ScanConfirmations.class);
        scans = new PaperSubmissionService(
                submissions,
                appointments,
                wiring.templates(),
                wiring.documents(),
                new DocumentCatalogService(
                        memberDocumentRepo,
                        wiring.documents(),
                        new SignatureSummaries(new SignatureRequestRepository())),
                memberNameResolver,
                notifier,
                confirmations);
    }

    @BeforeEach
    void appointment() {
        camp = eventRepo.create(
                wiring.station().id(),
                "Zeltlager",
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
        eventRegistrationRepo.create(camp.id(), lena.id(), DAY);
        consentName = "Einverständnis " + TEMPLATES.incrementAndGet();
        consent = wiring.templates()
                .create(
                        wiring.owner(),
                        letter(consentName, "{{member.fullName}}")
                                .forAppointments(true)
                                .build(),
                        manager.id())
                .id();
        requirements.setForEvent(wiring.owner(), camp.id(), List.of(consent));
        clearInvocations(notifier, confirmations);
    }

    private static UploadedFile scanFile() {
        return TestUploads.of("scan.pdf", "application/pdf", pdf);
    }

    private PaperSubmission handIn(StationMember by, StationPermission... permissions) {
        return scans.submit(as(by, permissions), camp, DAY, consent, lena.id(), null, scanFile());
    }

    private RequiredDocumentStatus lenasStatus() {
        return appointments
                .documentsToBring(as(guardian), camp, DAY, false)
                .own()
                .getFirst()
                .documents()
                .getFirst();
    }

    @Test
    void aGuardianHandsInTheChildsScanWithoutAnyRightToUpload() {
        var submission =
                scans.submit(as(guardian), camp, DAY, consent, lena.id(), "Einverständnis, unterschrieben", scanFile());

        assertEquals(PaperState.SUBMITTED, submission.state());
        var document = memberDocumentRepo.findById(submission.documentId()).orElseThrow();
        assertEquals("Einverständnis, unterschrieben", document.title());
        assertEquals(guardian.id(), document.uploadedBy());
        assertFalse(document.hidden());
        assertEquals(List.of(lena.id()), memberDocumentRepo.membersOf(document.id()));

        var status = lenasStatus();
        assertEquals(RequirementStatus.NOT_GENERATED, status.status(), "the scan does not stand for a copy");
        assertEquals(submission, status.paper());
    }

    @Test
    void aScanIsCalledAfterTheDocumentWithoutATitle() {
        var submission = scans.submit(as(lena), camp, DAY, consent, lena.id(), " ", scanFile());

        assertEquals(
                consentName,
                memberDocumentRepo
                        .findById(submission.documentId())
                        .orElseThrow()
                        .title());
    }

    @Test
    void nobodyElseHandsOneIn() {
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                () -> scans.submit(as(max), camp, DAY, consent, lena.id(), null, scanFile()));
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                () -> scans.submit(as(max), camp, DAY, consent, max.id(), null, scanFile()));
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                () -> scans.submit(
                        as(manager, StationPermission.EVENT_REGISTRATION),
                        camp,
                        DAY,
                        consent,
                        max.id(),
                        null,
                        scanFile()));
        int other = wiring.templates()
                .create(
                        wiring.owner(),
                        letter("Nicht verlangt", "{{member.fullName}}")
                                .forAppointments(true)
                                .build(),
                        manager.id())
                .id();
        refused(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_REQUIRED,
                () -> scans.submit(as(guardian), camp, DAY, other, lena.id(), null, scanFile()));
        assertTrue(memberDocumentRepo.findByMember(wiring.station().id(), lena.id(), true).stream()
                .noneMatch(document -> document.title().equals("Nicht verlangt")));
    }

    @Test
    void aManagersScanIsConfirmedAtOnceAndNoneFollows() {
        var submission = handIn(manager, StationPermission.EVENT_REGISTRATION);

        assertEquals(PaperState.CONFIRMED, submission.state());
        assertNotNull(submission.reviewedAt());
        assertEquals(PaperState.CONFIRMED, lenasStatus().paper().state());
        verify(confirmations).confirmed(any(), eq(submission));

        int filed = memberDocumentRepo
                .findByMember(wiring.station().id(), lena.id(), true)
                .size();
        refused(DocumentRefusal.DOCUMENT_SCAN_ALREADY_CONFIRMED, () -> handIn(guardian));
        assertEquals(
                filed,
                memberDocumentRepo
                        .findByMember(wiring.station().id(), lena.id(), true)
                        .size(),
                "a refused scan is not filed");
    }

    @Test
    void aNewScanReplacesTheOneThatWaits() {
        var first = handIn(guardian);
        var second = handIn(lena);

        assertTrue(memberDocumentRepo.findById(first.documentId()).isEmpty(), "the waiting scan is deleted");
        assertTrue(memberDocumentRepo.findById(second.documentId()).isPresent());
        assertEquals(second, lenasStatus().paper());
        refused(DocumentRefusal.DOCUMENT_SCAN_NOT_FOUND, () -> scans.confirm(as(manager), camp, first.id()));
    }

    @Test
    void aManagerConfirmsAWaitingScanOnce() {
        var submission = handIn(guardian);
        verify(confirmations, never()).confirmed(any(), any());

        var confirmed = scans.confirm(as(manager), camp, submission.id());

        verify(confirmations).confirmed(any(), eq(confirmed));
        assertEquals(PaperState.CONFIRMED, confirmed.state());
        assertEquals(PaperState.CONFIRMED, lenasStatus().paper().state());
        refused(DocumentRefusal.DOCUMENT_SCAN_NOT_WAITING, () -> scans.confirm(as(manager), camp, submission.id()));
        refused(
                DocumentRefusal.DOCUMENT_SCAN_NOT_WAITING,
                () -> scans.reject(as(manager), camp, submission.id(), "Zu spät"));
        refused(
                DocumentRefusal.DOCUMENT_SCAN_NOT_FOUND,
                () -> scans.confirm(as(manager), camp, submission.id() + 1000));
        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void aTurnedDownScanOpensTheDocumentAgainAndTellsTheHousehold() {
        var submission = handIn(guardian);
        refused(
                DocumentRefusal.DOCUMENT_SCAN_REASON_MISSING,
                () -> scans.reject(as(manager), camp, submission.id(), "  "));
        refused(
                DocumentRefusal.DOCUMENT_SCAN_REASON_MISSING,
                () -> scans.reject(as(manager), camp, submission.id(), null));
        refused(
                DocumentRefusal.DOCUMENT_SCAN_REASON_TOO_LONG,
                () -> scans.reject(
                        as(manager), camp, submission.id(), "x".repeat(PaperSubmissionService.MAX_REASON_LENGTH + 1)));

        var rejected = scans.reject(as(manager), camp, submission.id(), " Unterschrift fehlt ");

        assertEquals(PaperState.REJECTED, rejected.state());
        assertEquals("Unterschrift fehlt", rejected.rejectReason());
        assertEquals(rejected, lenasStatus().paper());
        assertTrue(
                memberDocumentRepo.findById(submission.documentId()).isPresent(), "the turned-down scan stays filed");
        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier)
                .notify(
                        eq(StationAudience.household(List.of(lena.id())).except(manager.id())),
                        eq(NotificationType.DOCUMENT_SCAN_REJECTED),
                        data.capture(),
                        eq(Delivery.EVERY_TIME));
        assertEquals(
                new NotificationParams.DocumentScanRejected(
                        consentName, memberNameResolver.identified(lena.id()), "Zeltlager", DAY, "Unterschrift fehlt"),
                data.getValue().params());

        var again = handIn(lena);
        assertEquals(PaperState.SUBMITTED, again.state());
        assertEquals(again, lenasStatus().paper());
    }

    /** The document a scan was for is named in the notice even where the station can no longer generate it. */
    @Test
    void aScanIsTurnedDownForADocumentTheStationNoLongerUses() {
        var submission = handIn(guardian);
        var elsewhere = stationRepo.create("Andere Wache " + System.nanoTime());
        query("UPDATE document_template SET station_id = :station_id WHERE id = :id;")
                .single(call().bind("station_id", elsewhere.id()).bind("id", consent))
                .update();

        var rejected = scans.reject(as(manager), camp, submission.id(), "Unterschrift fehlt");

        assertEquals(PaperState.REJECTED, rejected.state());
        var data = ArgumentCaptor.forClass(NotificationData.class);
        verify(notifier).notify(any(), eq(NotificationType.DOCUMENT_SCAN_REJECTED), data.capture(), any());
        assertEquals(
                consentName,
                ((NotificationParams.DocumentScanRejected) data.getValue().params()).documentName());
    }

    /**
     * Two first scans handed in at the same moment both find nothing standing; the second is refused
     * by name, and its file is not left behind.
     */
    @Test
    void aSecondFirstScanHandedInAtTheSameMomentIsRefused() {
        var blind = new PaperSubmissionService(
                new PaperSubmissionRepository() {
                    @Override
                    public Optional<PaperSubmission> lockStanding(PaperSubmissionRepository.Subject subject) {
                        return Optional.empty();
                    }
                },
                appointments,
                wiring.templates(),
                wiring.documents(),
                new DocumentCatalogService(
                        memberDocumentRepo,
                        wiring.documents(),
                        new SignatureSummaries(new SignatureRequestRepository())),
                memberNameResolver,
                notifier,
                confirmations);
        var first = blind.submit(as(guardian), camp, DAY, consent, lena.id(), null, scanFile());
        int filed = memberDocumentRepo
                .findByMember(wiring.station().id(), lena.id(), true)
                .size();

        refused(
                DocumentRefusal.DOCUMENT_SCAN_HANDED_IN_AT_ONCE,
                () -> blind.submit(as(lena), camp, DAY, consent, lena.id(), null, scanFile()));

        assertEquals(first, lenasStatus().paper());
        assertEquals(
                filed,
                memberDocumentRepo
                        .findByMember(wiring.station().id(), lena.id(), true)
                        .size(),
                "the refused scan is not filed");
    }

    @Test
    void aManagerReadsTheScanThroughTheAppointment() {
        var submission = handIn(guardian);

        var scan = scans.scan(as(manager), camp, submission.id());

        assertEquals(submission.documentId(), scan.document().id());
        assertArrayEquals(pdf, scan.data());
        refused(DocumentRefusal.DOCUMENT_SCAN_NOT_FOUND, () -> scans.scan(as(manager), camp, submission.id() + 1000));
    }

    /** Without registrations, whoever handed in a scan counts among the participants the organiser sees. */
    @Test
    void withoutRegistrationsAScanNamesItsParticipant() {
        var open = eventRepo.create(
                wiring.station().id(),
                "Tag der offenen Tür",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                START,
                START.plus(Duration.ofHours(4)),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        requirements.setForEvent(wiring.owner(), open.id(), List.of(consent));
        var submission = scans.submit(as(max), open, DAY, consent, max.id(), null, scanFile());

        var participants =
                appointments.documentsToBring(as(manager), open, DAY, true).participants();

        assertNotNull(participants);
        assertEquals(
                List.of(max.id()),
                participants.stream().map(participant -> participant.memberId()).toList());
        assertEquals(submission, participants.getFirst().documents().getFirst().paper());
    }

    @Test
    void anOrganiserSeesTheScanBesideEveryParticipant() {
        var submission = handIn(guardian);

        var participants =
                appointments.documentsToBring(as(manager), camp, DAY, true).participants();

        assertNotNull(participants);
        assertEquals(submission, participants.getFirst().documents().getFirst().paper());
        assertNull(appointments.documentsToBring(as(max), camp, DAY, false).participants());
    }
}
