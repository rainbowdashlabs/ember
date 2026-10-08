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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scans of signed paper copies: recorded waiting or confirmed at once, held while one stands, reviewed
 * once, the latest per document and participant, scoped to their appointment and station, and gone with
 * their scan.
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

    @Test
    void aScanWaitsUntilItIsConfirmedOnce() {
        int eventId = event();
        var subject = subject(eventId, participant);
        assertTrue(submissions.lockStanding(subject).isEmpty());

        var created = submissions.create(subject, scan(participant), participant, false);

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
        var created = submissions.create(subject(event(), participant), scan(participant), manager, true);

        assertEquals(PaperState.CONFIRMED, created.state());
        assertNotNull(created.reviewedAt());
        assertNull(created.rejectReason());
    }

    @Test
    void aTurnedDownScanStaysBesideTheNextOne() {
        int eventId = event();
        var subject = subject(eventId, participant);
        var first = submissions.create(subject, scan(participant), participant, false);
        var rejected = submissions
                .review(first.id(), PaperState.REJECTED, manager, "Unterschrift fehlt")
                .orElseThrow();
        assertEquals("Unterschrift fehlt", rejected.rejectReason());
        assertTrue(submissions.lockStanding(subject).isEmpty(), "a turned-down scan no longer stands");

        var second = submissions.create(subject, scan(participant), participant, false);
        var forOther = submissions.create(subject(eventId, other), scan(other), other, false);

        var latest = submissions.latest(eventId, DAY, List.of(participant, other));
        assertEquals(2, latest.size());
        assertTrue(latest.contains(second), "the newer scan of the participant is the one that shows");
        assertTrue(latest.contains(forOther));
        assertEquals(List.of(), submissions.latest(eventId, DAY.plusDays(1), List.of(participant)));
    }

    @Test
    void onlyOneScanStandsAtATime() {
        var subject = subject(event(), participant);
        submissions.create(subject, scan(participant), participant, false);

        assertThrows(RuntimeException.class, () -> submissions.create(subject, scan(participant), participant, false));
    }

    @Test
    void aScanIsFoundOnlyAtItsAppointmentAndStation() {
        int eventId = event();
        var created = submissions.create(subject(eventId, participant), scan(participant), participant, false);

        assertEquals(
                created, submissions.find(station.id(), eventId, created.id()).orElseThrow());
        assertTrue(submissions.find(station.id(), event(), created.id()).isEmpty());
        assertTrue(submissions.find(station.id() + 1000, eventId, created.id()).isEmpty());
    }

    @Test
    void aSubmissionGoesOnItsOwnOrWithItsScan() {
        int eventId = event();
        var removed = submissions.create(subject(eventId, participant), scan(participant), participant, false);
        submissions.delete(removed.id());
        assertTrue(submissions.find(station.id(), eventId, removed.id()).isEmpty());

        int document = scan(other);
        var withScan = submissions.create(subject(eventId, other), document, other, false);
        memberDocumentRepo.delete(document);
        assertTrue(submissions.find(station.id(), eventId, withScan.id()).isEmpty());
    }
}
