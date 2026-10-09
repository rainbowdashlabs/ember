/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import dev.chojo.ember.feature.generator.repository.PaperSubmissionRepository.Subject;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scans of signed paper copies: recorded waiting or confirmed at once, held while one stands, reviewed
 * once, the latest per document and participant, scoped to their appointment and station, and kept on
 * record when their scan is deleted.
 */
class PaperSubmissionRepositoryTest extends RepositoryTestBase {
    private static final PaperSubmissionRepository submissions = new PaperSubmissionRepository();
    private static final LocalDate DAY = LocalDate.parse("2026-10-10");
    private static final AtomicInteger EVENTS = new AtomicInteger();

    private static Station station;
    private static int participant;
    private static int other;
    private static int manager;
    private static int templateId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Scan Wache");
        participant = member("scan-participant@test.com");
        other = member("scan-other@test.com");
        manager = member("scan-manager@test.com");
        var draft = new DocumentTemplateDraft(
                "Einverständnis",
                "Einverständnis",
                "Einverständnis",
                List.of(),
                false,
                false,
                false,
                false,
                false,
                30,
                RestrictionMode.AND,
                DocumentLanguage.DE,
                null,
                null,
                LetterContent.blank());
        templateId = new DocumentTemplateRepository()
                .create(new Owner.Station(station.id()), draft, manager)
                .id();
    }

    private static int member(String email) {
        return stationMemberRepo
                .create(station.id(), accountRepo.create(email, "Scan", "Test").id())
                .id();
    }

    private static int event() {
        var start = Instant.parse("2026-10-10T08:00:00Z");
        return eventRepo
                .create(
                        station.id(),
                        "Zeltlager " + EVENTS.incrementAndGet(),
                        null,
                        StationEvent.EventType.ONE_TIME,
                        null,
                        start,
                        start.plus(Duration.ofHours(4)),
                        null,
                        true,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .id();
    }

    private static int scan(int memberId) {
        return memberDocumentRepo
                .create(
                        station.id(),
                        "Scan",
                        "scan.pdf",
                        "application/pdf",
                        10,
                        false,
                        false,
                        Uploader.member(memberId),
                        List.of(memberId))
                .id();
    }

    private static Subject subject(int eventId, int memberId) {
        return new Subject(station.id(), eventId, DAY, templateId, memberId);
    }

    private static PaperSubmission record(Subject subject, int documentId, int submittedBy, boolean confirmed) {
        return submissions.create(subject, documentId, submittedBy, confirmed).orElseThrow();
    }

    @Test
    void aScanWaitsUntilItIsConfirmedOnce() {
        int eventId = event();
        var subject = subject(eventId, participant);
        assertTrue(submissions.lockStanding(subject).isEmpty());

        var created = record(subject, scan(participant), participant, false);

        assertEquals(PaperState.SUBMITTED, created.state());
        assertEquals(eventId, created.eventId());
        assertEquals(DAY, created.eventDate());
        assertEquals(templateId, created.templateId());
        assertEquals(participant, created.memberId());
        assertNull(created.reviewedAt());
        assertEquals(created, submissions.lockStanding(subject).orElseThrow());

        var confirmed = submissions
                .review(created.id(), PaperState.CONFIRMED, manager, null)
                .orElseThrow();
        assertEquals(PaperState.CONFIRMED, confirmed.state());
        assertNotNull(confirmed.reviewedAt());
        assertTrue(
                submissions
                        .review(created.id(), PaperState.REJECTED, manager, "Zu spät")
                        .isEmpty(),
                "a scan is decided once");
        assertEquals(
                PaperState.CONFIRMED,
                submissions.lockStanding(subject).orElseThrow().state());
    }

    @Test
    void aManagersScanIsConfirmedAtOnce() {
        var created = record(subject(event(), participant), scan(participant), manager, true);

        assertEquals(PaperState.CONFIRMED, created.state());
        assertNotNull(created.reviewedAt());
        assertNull(created.rejectReason());
    }

    @Test
    void aTurnedDownScanStaysBesideTheNextOne() {
        int eventId = event();
        var subject = subject(eventId, participant);
        var first = record(subject, scan(participant), participant, false);
        var rejected = submissions
                .review(first.id(), PaperState.REJECTED, manager, "Unterschrift fehlt")
                .orElseThrow();
        assertEquals("Unterschrift fehlt", rejected.rejectReason());
        assertTrue(submissions.lockStanding(subject).isEmpty(), "a turned-down scan no longer stands");

        var second = record(subject, scan(participant), participant, false);
        var forOther = record(subject(eventId, other), scan(other), other, false);

        var latest = submissions.latest(eventId, DAY, List.of(participant, other));
        assertEquals(2, latest.size());
        assertTrue(latest.contains(second), "the newer scan of the participant is the one that shows");
        assertTrue(latest.contains(forOther));
        assertEquals(List.of(), submissions.latest(eventId, DAY.plusDays(1), List.of(participant)));
    }

    /** A second scan arriving while one stands, as two hand-ins at the same moment do, is not recorded. */
    @Test
    void onlyOneScanStandsAtATime() {
        var subject = subject(event(), participant);
        var standing = record(subject, scan(participant), participant, false);

        assertTrue(submissions
                .create(subject, scan(participant), participant, false)
                .isEmpty());
        assertEquals(standing, submissions.lockStanding(subject).orElseThrow());
    }

    @Test
    void aScanIsFoundOnlyAtItsAppointmentAndStation() {
        int eventId = event();
        var created = record(subject(eventId, participant), scan(participant), participant, false);

        assertEquals(
                created, submissions.find(station.id(), eventId, created.id()).orElseThrow());
        assertTrue(submissions.find(station.id(), event(), created.id()).isEmpty());
        assertTrue(submissions.find(station.id() + 1000, eventId, created.id()).isEmpty());
    }

    @Test
    void aSubmissionGoesOnItsOwn() {
        int eventId = event();
        var removed = record(subject(eventId, participant), scan(participant), participant, false);
        submissions.delete(removed.id());
        assertTrue(submissions.find(station.id(), eventId, removed.id()).isEmpty());
        assertEquals(0, rowsOf(removed.id()));
    }

    /**
     * Deleting a scan keeps its submission on record, turned down ones with their reason, but it no longer
     * stands, cannot be reviewed and does not show, so a new scan can be handed in.
     */
    @Test
    void aDeletedScanLeavesItsSubmissionOnRecordAndTheDocumentOpen() {
        int eventId = event();
        var subject = subject(eventId, other);
        int rejectedScan = scan(other);
        var rejected = record(subject, rejectedScan, other, false);
        submissions.review(rejected.id(), PaperState.REJECTED, manager, "Unscharf");
        int document = scan(other);
        var waiting = record(subject, document, other, false);

        memberDocumentRepo.delete(rejectedScan);
        memberDocumentRepo.delete(document);

        assertEquals(1, rowsOf(rejected.id()), "the turned-down scan stays on record");
        assertEquals(1, rowsOf(waiting.id()));
        assertTrue(submissions.find(station.id(), eventId, waiting.id()).isEmpty());
        assertTrue(submissions.lockStanding(subject).isEmpty());
        assertTrue(submissions
                .review(waiting.id(), PaperState.CONFIRMED, manager, null)
                .isEmpty());
        assertEquals(List.of(), submissions.latest(eventId, DAY, List.of(other)));
        var next = record(subject, scan(other), other, false);
        assertEquals(List.of(next), submissions.latest(eventId, DAY, List.of(other)));
    }

    private static int rowsOf(int submissionId) {
        return query("SELECT count(*) AS count FROM event_document_submission WHERE id = :id;")
                .single(call().bind("id", submissionId))
                .map(row -> row.getInt("count"))
                .first()
                .orElseThrow();
    }
}
