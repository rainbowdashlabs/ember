/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService.AppointmentDocuments;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService.GeneratedDocumentResponse;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.ParticipantCopyService;
import dev.chojo.ember.feature.generator.service.TemplateQuery;
import dev.chojo.ember.feature.generator.service.TemplateQuery.TemplatePage;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The documents appointments ask for, over HTTP: the lists are written with the rights that write
 * appointments and appointment templates, the copies are asked for with the date and only the right to
 * see the appointment.
 */
class AppointmentDocumentRoutesTest {
    private static final Owner.Station OWNER = new Owner.Station(3);
    private static final LocalDate DAY = LocalDate.parse("2026-09-27");
    private static final RequiredTemplate CONSENT =
            new RequiredTemplate(8, "Einverständnis", DocumentTemplateKind.PDF, 1, false, null);
    private static final TemplatePage OFFERED = new TemplatePage(
            List.of(new DocumentTemplateSummary(
                    8,
                    "Einverständnis",
                    DocumentTemplateKind.PDF,
                    false,
                    true,
                    false,
                    false,
                    1,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    null,
                    null)),
            1,
            0,
            TemplateQuery.DEFAULT_SIZE);

    private static final byte[] COPY = "%PDF-1.4 copy".getBytes(StandardCharsets.US_ASCII);

    private EventRequirementService requirements;
    private AppointmentDocumentService documents;
    private ParticipantCopyService copies;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        requirements = mock(EventRequirementService.class);
        documents = mock(AppointmentDocumentService.class);
        var visibility = mock(EventVisibility.class);
        var eventTemplates = mock(EventTemplateService.class);
        var event = event();
        when(visibility.requireVisibleEvent(any(), eq(5))).thenReturn(event);
        when(visibility.requireVisibleEvent(any(), eq(6))).thenThrow(EventRefusal.EVENT_NOT_YOURS_TO_SEE.raise());
        when(eventTemplates.findById(7)).thenReturn(Optional.of(eventTemplate(7, 3)));
        when(eventTemplates.findById(9)).thenReturn(Optional.of(eventTemplate(9, 4)));
        when(requirements.offered(eq(OWNER), any())).thenReturn(OFFERED);
        when(requirements.forEvent(5)).thenReturn(List.of(CONSENT));
        when(requirements.setForEvent(OWNER, 5, List.of(8))).thenReturn(List.of(CONSENT));
        when(requirements.forEventTemplate(7)).thenReturn(List.of(CONSENT));
        when(requirements.setForEventTemplate(OWNER, 7, List.of())).thenReturn(List.of());
        when(documents.documentsToBring(any(), eq(event), eq(DAY), anyBoolean()))
                .thenReturn(new AppointmentDocuments(List.of(CONSENT), List.of(), null));
        when(documents.generate(any(), eq(event), eq(DAY), eq(8), eq(11)))
                .thenReturn(new GeneratedDocumentResponse(40, 41, "Einverständnis", List.of()));
        copies = mock(ParticipantCopyService.class);
        when(copies.copyOf(any(), eq(event), eq(DAY), eq(8), eq(11)))
                .thenReturn(new ParticipantCopyService.Copy(copyDocument(), COPY));
        when(copies.copyOf(any(), eq(event), eq(DAY), eq(8), eq(12)))
                .thenThrow(DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS.raise());
        harness = RouteHarness.serving(
                new AppointmentDocumentRoutes(requirements, documents, copies, visibility, eventTemplates));
    }

    private static Document copyDocument() {
        return new Document(
                40,
                3,
                "Einverständnis",
                "einverstaendnis.pdf",
                "application/pdf",
                COPY.length,
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
                "Berlin-Marathon",
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

    private static EventTemplate eventTemplate(int id, int stationId) {
        return new EventTemplate(
                id, stationId, "Lauf", null, null, null, null, null, null, null, null, RestrictionMode.AND, null, null);
    }

    @Test
    void anEditorOfAppointmentsSetsWhatTheyAskFor() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.EVENT_EDIT));
            assertEquals(
                    "Einverständnis",
                    json(client.get(PREFIX + "/document-requirements/templates", editor))
                            .path("items")
                            .path(0)
                            .path("name")
                            .asString());
            assertEquals(
                    200,
                    client.get(PREFIX + "/events/5/document-requirements", editor)
                            .code());
            assertEquals(
                    8,
                    json(client.put(PREFIX + "/events/5/document-requirements", body("{\"templateIds\": [8]}"), editor))
                            .path(0)
                            .path("templateId")
                            .asInt());
            assertEquals(
                    403,
                    client.put(PREFIX + "/event-templates/7/document-requirements", body("{}"), editor)
                            .code());
        });

        verify(requirements).setForEvent(OWNER, 5, List.of(8));
    }

    @Test
    void anEditorOfAppointmentTemplatesSetsWhatTheyHandOn() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.EVENT_MANAGE_TEMPLATE));
            assertEquals(
                    200,
                    client.get(PREFIX + "/document-requirements/templates", editor)
                            .code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/event-templates/7/document-requirements", editor)
                            .code());
            assertEquals(
                    200,
                    client.put(PREFIX + "/event-templates/7/document-requirements", body("{}"), editor)
                            .code());
            assertEquals(
                    404,
                    client.get(PREFIX + "/event-templates/9/document-requirements", editor)
                            .code());
            assertEquals(
                    403,
                    client.put(PREFIX + "/events/5/document-requirements", body("{}"), editor)
                            .code());
        });

        verify(requirements).setForEventTemplate(OWNER, 7, List.of());
        verify(requirements, never()).setForEventTemplate(any(), eq(9), any());
    }

    @Test
    void aParticipantAsksForTheirCopyWithTheDate() {
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3, StationPermission.USER));
            assertEquals(
                    200,
                    client.get(PREFIX + "/events/5/documents-to-bring?date=2026-09-27", member)
                            .code());
            verify(documents).documentsToBring(any(), any(), eq(DAY), eq(false));
            assertEquals(
                    200,
                    client.get(
                                    PREFIX + "/events/5/documents-to-bring?date=2026-09-27",
                                    harness.as(TestSessions.member(
                                            3, StationPermission.USER, StationPermission.EVENT_REGISTRATION)))
                            .code());
            verify(documents).documentsToBring(any(), any(), eq(DAY), eq(true));
            var generated =
                    client.post(PREFIX + "/events/5/documents-to-bring/8/members/11?date=2026-09-27", null, member);
            assertEquals(201, generated.code());
            assertEquals(40, json(generated).path("documentId").asInt());
            assertEquals(
                    EventRefusal.EVENT_DOCUMENTS_DATE_MISSING,
                    refusalOf(client.get(PREFIX + "/events/5/documents-to-bring", member)));
            assertEquals(
                    EventRefusal.EVENT_DOCUMENTS_DATE_MISSING,
                    refusalOf(client.get(PREFIX + "/events/5/documents-to-bring?date=morgen", member)));
            assertEquals(
                    EventRefusal.EVENT_NOT_YOURS_TO_SEE,
                    refusalOf(client.post(
                            PREFIX + "/events/6/documents-to-bring/8/members/11?date=2026-09-27", null, member)));
            assertEquals(
                    403,
                    client.get(PREFIX + "/events/5/document-requirements", member)
                            .code());
        });

        verify(documents).generate(any(), any(), eq(DAY), eq(8), eq(11));
        verify(documents, never()).generate(any(), any(), any(), anyInt(), eq(12));
        verify(requirements, never()).setForEvent(any(), anyInt(), any());
    }

    @Test
    void aManagerOfTheRegistrationsDownloadsAParticipantsCopy() {
        harness.run((server, client) -> {
            var manager =
                    harness.as(TestSessions.member(3, StationPermission.USER, StationPermission.EVENT_REGISTRATION));
            var served = client.get(PREFIX + "/events/5/documents-to-bring/8/members/11/copy?date=2026-09-27", manager);
            assertEquals(200, served.code());
            assertEquals(
                    new String(COPY, StandardCharsets.US_ASCII), served.body().string());
            assertEquals(
                    DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                    refusalOf(client.get(
                            PREFIX + "/events/5/documents-to-bring/8/members/12/copy?date=2026-09-27", manager)));
            assertEquals(
                    EventRefusal.EVENT_DOCUMENTS_DATE_MISSING,
                    refusalOf(client.get(PREFIX + "/events/5/documents-to-bring/8/members/11/copy", manager)));
            assertEquals(
                    403,
                    client.get(
                                    PREFIX + "/events/5/documents-to-bring/8/members/11/copy?date=2026-09-27",
                                    harness.as(TestSessions.member(3, StationPermission.USER)))
                            .code());
        });

        verify(copies).copyOf(any(), any(), eq(DAY), eq(8), eq(11));
    }

    @Test
    void aRefusedCopyAnswersWithItsRefusal() {
        when(documents.generate(any(), any(), eq(DAY), eq(8), eq(12)))
                .thenThrow(DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS.raise());
        harness.run((server, client) -> assertEquals(
                DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_YOURS,
                refusalOf(client.post(
                        PREFIX + "/events/5/documents-to-bring/8/members/12?date=2026-09-27",
                        null,
                        harness.as(TestSessions.member(3, StationPermission.USER))))));
    }
}
