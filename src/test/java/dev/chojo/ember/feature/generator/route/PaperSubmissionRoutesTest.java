/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.generator.entity.PaperState;
import dev.chojo.ember.feature.generator.entity.PaperSubmission;
import dev.chojo.ember.feature.generator.service.PaperSubmissionService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scans of signed paper copies over HTTP: handed in with the date and only the right to see the
 * appointment, read, confirmed and turned down with the right to manage the registrations.
 */
class PaperSubmissionRoutesTest {
    private static final LocalDate DAY = LocalDate.parse("2026-10-10");
    private static final byte[] SCAN = "%PDF-1.4 scan".getBytes(StandardCharsets.US_ASCII);
    private static final PaperSubmission WAITING = submission(PaperState.SUBMITTED, null);

    private PaperSubmissionService submissions;
    private RouteHarness harness;
    private StationEvent event;

    private static PaperSubmission submission(PaperState state, String reason) {
        return new PaperSubmission(20, 5, DAY, 8, 11, 40, state, Instant.EPOCH, null, reason);
    }

    @BeforeEach
    void setup() {
        submissions = mock(PaperSubmissionService.class);
        var visibility = mock(EventVisibility.class);
        event = event();
        when(visibility.requireVisibleEvent(any(), eq(5))).thenReturn(event);
        when(visibility.requireVisibleEvent(any(), eq(6))).thenThrow(EventRefusal.EVENT_NOT_YOURS_TO_SEE.raise());
        when(submissions.submit(any(), eq(event), eq(DAY), eq(8), eq(11), any(), any()))
                .thenReturn(WAITING);
        when(submissions.confirm(any(), eq(event), eq(20))).thenReturn(submission(PaperState.CONFIRMED, null));
        when(submissions.confirm(any(), eq(event), eq(21))).thenThrow(DocumentRefusal.DOCUMENT_SCAN_NOT_FOUND.raise());
        when(submissions.reject(any(), eq(event), eq(20), eq("Unterschrift fehlt")))
                .thenReturn(submission(PaperState.REJECTED, "Unterschrift fehlt"));
        when(submissions.reject(any(), eq(event), eq(20), isNull()))
                .thenThrow(DocumentRefusal.DOCUMENT_SCAN_REASON_MISSING.raise());
        when(submissions.scan(any(), eq(event), eq(20))).thenReturn(new PaperSubmissionService.Scan(document(), SCAN));
        harness = RouteHarness.serving(new PaperSubmissionRoutes(submissions, visibility));
    }

    private static Document document() {
        return new Document(
                40,
                3,
                "Einverständnis",
                "scan.pdf",
                "application/pdf",
                SCAN.length,
                false,
                false,
                false,
                11,
                null,
                Instant.EPOCH,
                false);
    }

    private static StationEvent event() {
        return new StationEvent(
                5,
                3,
                "Zeltlager",
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                true,
                null,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void aParticipantHandsInAScanWithTheDate() {
        harness.run((server, client) -> {
            var participant = harness.as(TestSessions.member(3, StationPermission.USER));
            var created = client.request(
                    PREFIX + "/events/5/documents-to-bring/8/members/11/scan?date=2026-10-10",
                    participant.andThen(TestUploads.multipart("scan.pdf", SCAN, Map.of("title", "Unterschrieben"))));
            assertEquals(201, created.code());
            assertEquals("SUBMITTED", json(created).path("state").asString());
            assertEquals(
                    EventRefusal.EVENT_DOCUMENTS_DATE_MISSING,
                    refusalOf(client.request(
                            PREFIX + "/events/5/documents-to-bring/8/members/11/scan",
                            participant.andThen(TestUploads.multipart("scan.pdf", SCAN)))));
            assertEquals(
                    EventRefusal.EVENT_NOT_YOURS_TO_SEE,
                    refusalOf(client.request(
                            PREFIX + "/events/6/documents-to-bring/8/members/11/scan?date=2026-10-10",
                            participant.andThen(TestUploads.multipart("scan.pdf", SCAN)))));
        });

        verify(submissions).submit(any(), eq(event), eq(DAY), eq(8), eq(11), eq("Unterschrieben"), any());
    }

    @Test
    void aManagerOfTheRegistrationsReadsConfirmsAndTurnsDown() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.EVENT_REGISTRATION));
            var content = client.get(PREFIX + "/events/5/document-scans/20/content", manager);
            assertEquals(200, content.code());
            assertEquals(
                    new String(SCAN, StandardCharsets.US_ASCII), content.body().string());
            assertEquals(
                    "CONFIRMED",
                    json(client.post(PREFIX + "/events/5/document-scans/20/confirm", body("{}"), manager))
                            .path("state")
                            .asString());
            assertEquals(
                    DocumentRefusal.DOCUMENT_SCAN_NOT_FOUND,
                    refusalOf(client.post(PREFIX + "/events/5/document-scans/21/confirm", body("{}"), manager)));
            var rejected = json(client.post(
                    PREFIX + "/events/5/document-scans/20/reject",
                    body("{\"reason\": \"Unterschrift fehlt\"}"),
                    manager));
            assertEquals("REJECTED", rejected.path("state").asString());
            assertEquals("Unterschrift fehlt", rejected.path("rejectReason").asString());
            assertEquals(
                    DocumentRefusal.DOCUMENT_SCAN_REASON_MISSING,
                    refusalOf(client.post(PREFIX + "/events/5/document-scans/20/reject", body("{}"), manager)));
        });
    }

    @Test
    void aParticipantNeitherReadsNorDecides() {
        harness.run((server, client) -> {
            var participant = harness.as(TestSessions.member(3, StationPermission.USER));
            assertEquals(
                    403,
                    client.get(PREFIX + "/events/5/document-scans/20/content", participant)
                            .code());
            assertEquals(
                    403,
                    client.post(PREFIX + "/events/5/document-scans/20/confirm", body("{}"), participant)
                            .code());
            assertEquals(
                    403,
                    client.post(
                                    PREFIX + "/events/5/document-scans/20/reject",
                                    body("{\"reason\": \"Nein\"}"),
                                    participant)
                            .code());
        });

        verify(submissions, never()).confirm(any(), any(), anyInt());
        verify(submissions, never()).reject(any(), any(), anyInt(), any());
    }
}
