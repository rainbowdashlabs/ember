/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Letterhead;
import dev.chojo.ember.feature.generator.service.DocumentTemplateRequest;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.PlaceholderCatalogueResponse;
import dev.chojo.ember.feature.generator.service.LetterImportService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The template routes over HTTP: only an editor of templates reaches them, and each hands the
 * station from the session to the service.
 */
class DocumentTemplateRoutesTest {
    private static final Owner.Station OWNER = new Owner.Station(3);
    private static final DocumentTemplateResponse TEMPLATE = new DocumentTemplateResponse(
            8,
            "Bescheinigung",
            DocumentTemplateKind.LETTER,
            "t",
            "f",
            List.of(),
            false,
            false,
            false,
            false,
            30,
            RestrictionAudience.empty(),
            null,
            Letterhead.empty(),
            "",
            LetterPage.defaults(),
            1,
            Instant.EPOCH,
            null);

    private DocumentTemplateService service;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        service = mock(DocumentTemplateService.class);
        when(service.list(any(), anyBoolean())).thenReturn(List.of());
        when(service.requireOwned(OWNER, 8)).thenReturn(template(8));
        when(service.detail(any(), anyInt())).thenReturn(TEMPLATE);
        when(service.create(any(), any(), anyInt())).thenReturn(TEMPLATE);
        when(service.update(any(), anyInt(), any(), anyInt())).thenReturn(TEMPLATE);
        when(service.setArchived(any(), anyInt(), anyBoolean(), anyInt())).thenReturn(TEMPLATE);
        when(service.catalogue(any())).thenReturn(new PlaceholderCatalogueResponse(List.of(), List.of()));
        harness = RouteHarness.serving(new DocumentTemplateRoutes(service, mock(LetterImportService.class)));
    }

    static DocumentTemplate template(int id) {
        return new DocumentTemplate(
                id,
                3,
                DocumentTemplateKind.LETTER,
                "Bescheinigung",
                "t",
                "f",
                List.of(),
                false,
                false,
                false,
                false,
                30,
                RestrictionMode.AND,
                null,
                1,
                Instant.EPOCH,
                Instant.EPOCH,
                null);
    }

    @Test
    void anEditorOfTemplatesReadsAndWritesThem() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    200,
                    client.get(PREFIX + "/document-templates?archived=true", editor)
                            .code());
            assertEquals(
                    200, client.get(PREFIX + "/document-placeholders", editor).code());
            assertEquals(
                    "Bescheinigung",
                    json(client.get(PREFIX + "/document-templates/8", editor))
                            .path("name")
                            .asString());
            assertEquals(
                    201,
                    client.post(PREFIX + "/document-templates", body("{\"name\": \"Neu\", \"legal\": true}"), editor)
                            .code());
            assertEquals(
                    200,
                    client.put(PREFIX + "/document-templates/8", body("{\"name\": \"Neu\"}"), editor)
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/document-templates/8/archive", null, editor)
                            .code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/document-templates/8/restore", null, editor)
                            .code());
        });

        verify(service).list(OWNER, true);
        var request = ArgumentCaptor.forClass(DocumentTemplateRequest.class);
        verify(service).create(eq(OWNER), request.capture(), anyInt());
        assertEquals("Neu", request.getValue().name());
        assertEquals(true, request.getValue().legal());
        verify(service).setArchived(eq(OWNER), eq(8), eq(true), anyInt());
        verify(service).setArchived(eq(OWNER), eq(8), eq(false), anyInt());
    }

    /** Filing documents for members is not the same as writing the templates they come from. */
    @Test
    void aFilerOfMemberDocumentsWritesNoTemplate() {
        harness.run((server, client) -> {
            var filer = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(403, client.get(PREFIX + "/document-templates", filer).code());
            assertEquals(
                    403,
                    client.post(PREFIX + "/document-templates", body("{}"), filer)
                            .code());
        });
    }

    @Test
    void theDocumentManagerWritesTemplatesAndTheEditorOfTemplatesReachesNoMember() {
        assertTrue(StationPermission.DOCUMENT_MANAGER.allChildren().contains(StationPermission.DOCUMENT_TEMPLATE_EDIT));
        assertFalse(StationPermission.DOCUMENT_TEMPLATE_EDIT
                .allChildren()
                .contains(StationPermission.DOCUMENT_READ_MEMBER));
    }
}
