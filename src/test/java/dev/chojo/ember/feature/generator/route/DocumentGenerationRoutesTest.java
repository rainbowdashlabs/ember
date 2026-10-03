/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService.GeneratedDocumentResponse;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.PreviewResponse;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.SelfServiceDocumentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
 * Generating over HTTP: a manager needs the right to file member documents, self service needs only a
 * sign-in, and every member and template named in the address has to be of the reader's station.
 */
class DocumentGenerationRoutesTest {
    private static final Owner.Station OWNER = new Owner.Station(3);
    private static final GeneratedDocumentResponse FILED =
            new GeneratedDocumentResponse(40, 41, "Bescheinigung", List.of());

    private DocumentGenerationService generation;
    private SelfServiceDocumentService selfService;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        generation = mock(DocumentGenerationService.class);
        selfService = mock(SelfServiceDocumentService.class);
        var templates = mock(DocumentTemplateService.class);
        var members = mock(StationMemberService.class);
        when(templates.requireUsable(OWNER.stationId(), 8)).thenReturn(DocumentTemplateRoutesTest.template(8));
        when(templates.requireUsable(OWNER.stationId(), 9))
                .thenThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE.raise());
        when(members.findById(11)).thenReturn(Optional.of(member(11, 3)));
        when(members.findById(12)).thenReturn(Optional.of(member(12, 4)));
        when(generation.usable(OWNER)).thenReturn(List.of());
        when(generation.preview(any(), eq(8), eq(11))).thenReturn(new PreviewResponse("JVBER", List.of(), List.of()));
        when(generation.previewDraft(any(), any(), any(), any()))
                .thenReturn(new PreviewResponse("JVBER", List.of(), List.of()));
        when(generation.generate(any(), eq(8), eq(11))).thenReturn(FILED);
        when(selfService.offers(any(), eq(11))).thenReturn(List.of());
        when(selfService.generate(any(), eq(8), eq(11))).thenReturn(FILED);
        harness = RouteHarness.serving(new DocumentGenerationRoutes(generation, selfService, templates, members));
    }

    private static StationMember member(int id, int stationId) {
        return new StationMember(id, stationId, null, 1, false, null, "Lena", StationUserType.MEMBER, LocalDate.EPOCH);
    }

    @Test
    void aManagerPreviewsAndGeneratesForAMemberOfTheStation() {
        harness.run((server, client) -> {
            var filer = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(
                    200,
                    client.get(PREFIX + "/document-generation/templates", filer).code());
            assertEquals(
                    "JVBER",
                    json(client.post(PREFIX + "/document-generation/templates/8/members/11/preview", null, filer))
                            .path("pdfBase64")
                            .asString());
            var generated = client.post(PREFIX + "/document-generation/templates/8/members/11", null, filer);
            assertEquals(201, generated.code());
            assertEquals(40, json(generated).path("documentId").asInt());
            assertEquals(
                    404,
                    client.post(PREFIX + "/document-generation/templates/8/members/12", null, filer)
                            .code());
            assertEquals(
                    DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE,
                    refusalOf(client.post(PREFIX + "/document-generation/templates/9/members/11", null, filer)));
        });

        verify(generation).generate(any(), eq(8), eq(11));
        verify(generation, never()).generate(any(), anyInt(), eq(12));
    }

    @Test
    void generatingForAnotherMemberNeedsTheRightToFileTheirDocuments() {
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_READ_MEMBER));
            assertEquals(
                    403,
                    client.post(PREFIX + "/document-generation/templates/8/members/11", null, reader)
                            .code());
            assertEquals(
                    403,
                    client.get(PREFIX + "/document-generation/templates", reader)
                            .code());
        });
    }

    @Test
    void theEditorDrawsADraftForAMemberOfTheStationOnly() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    200,
                    client.post(
                                    PREFIX + "/document-template-preview",
                                    body("{\"template\": {\"body\": [{\"sortOrder\": 0, \"cells\": [{\"sortOrder\": 0,"
                                            + " \"contentType\": \"MARKDOWN\", \"content\": \"{{today}}\"}]}]}}"),
                                    editor)
                            .code());
            assertEquals(
                    404,
                    client.post(PREFIX + "/document-template-preview", body("{\"memberId\": 12}"), editor)
                            .code());
            assertEquals(
                    403,
                    client.post(
                                    PREFIX + "/document-template-preview",
                                    body("{}"),
                                    harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER)))
                            .code());
        });

        verify(generation).previewDraft(any(), any(), isNull(), isNull());
    }

    @Test
    void selfServiceNeedsOnlyASignIn() {
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(3));
            assertEquals(
                    200,
                    client.get(PREFIX + "/self-service/documents/11", member).code());
            assertEquals(
                    201,
                    client.post(PREFIX + "/self-service/documents/11/templates/8", null, member)
                            .code());
            assertEquals(
                    404,
                    client.get(PREFIX + "/self-service/documents/12", member).code());
        });

        verify(selfService).generate(any(), eq(8), eq(11));
    }
}
