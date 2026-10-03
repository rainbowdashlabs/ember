/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.PreviewResponse;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.PlaceholderCatalogueResponse;
import dev.chojo.ember.feature.generator.service.LetterImportService;
import dev.chojo.ember.feature.generator.service.LetterImportService.LetterImport;
import dev.chojo.ember.feature.generator.service.PdfTemplateService;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The association's template routes over HTTP: only who may write the association's templates reaches
 * them, the association comes from the session, and a station's right to write its own templates opens
 * none of them.
 */
class AssociationDocumentTemplateRoutesTest {
    private static final Owner.Association OWNER = new Owner.Association(7);

    private DocumentTemplateService templates;
    private DocumentGenerationService generation;
    private LetterImportService imports;
    private PdfTemplateService pdfs;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        templates = mock(DocumentTemplateService.class);
        generation = mock(DocumentGenerationService.class);
        imports = mock(LetterImportService.class);
        pdfs = mock(PdfTemplateService.class);
        var template = DocumentTemplateRoutesTest.TEMPLATE;
        when(templates.list(any(), anyBoolean())).thenReturn(List.of());
        when(templates.detail(any(), anyInt())).thenReturn(template);
        when(templates.create(any(), any(), anyInt())).thenReturn(template);
        when(templates.update(any(), anyInt(), any(), anyInt())).thenReturn(template);
        when(templates.setArchived(any(), anyInt(), anyBoolean(), anyInt())).thenReturn(template);
        when(templates.catalogue(any())).thenReturn(new PlaceholderCatalogueResponse(List.of()));
        when(generation.previewAssociationDraft(any(), any(), any(), any()))
                .thenReturn(new PreviewResponse("JVBER", List.of(), List.of()));
        when(imports.read(any(LetterImportService.Importer.class), any()))
                .thenReturn(new LetterImport(List.of(), List.of(), List.of()));
        when(pdfs.upload(any(), anyInt(), any(), anyInt())).thenReturn(template);
        when(pdfs.current(OWNER, 8))
                .thenReturn(Optional.of(
                        new PdfTemplateService.Download("form.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII))));
        harness = RouteHarness.serving(new AssociationDocumentTemplateRoutes(templates, generation, imports, pdfs));
    }

    @Test
    void anEditorOfTheAssociationsTemplatesWritesThemForTheAssociation() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT));
            String base = PREFIX + "/cluster/document-templates";
            assertEquals(200, client.get(base, editor).code());
            assertEquals(
                    200,
                    client.get(PREFIX + "/cluster/document-placeholders", editor)
                            .code());
            assertEquals(
                    201,
                    client.post(base, body("{\"name\": \"Verbandsbrief\"}"), editor)
                            .code());
            assertEquals(200, client.get(base + "/8", editor).code());
            assertEquals(
                    200,
                    client.put(base + "/8", body("{\"name\": \"Neu\"}"), editor).code());
            assertEquals(200, client.post(base + "/8/archive", null, editor).code());
            assertEquals(200, client.post(base + "/8/restore", null, editor).code());
            assertEquals(
                    200,
                    client.post(PREFIX + "/cluster/document-template-preview", body("{}"), editor)
                            .code());
            assertEquals(
                    200,
                    client.request(
                                    PREFIX + "/cluster/document-template-import",
                                    editor.andThen(TestUploads.multipart("brief.docx", new byte[] {1})))
                            .code());
            assertEquals(
                    200,
                    client.request(base + "/8/pdf", editor.andThen(TestUploads.multipart("form.pdf", new byte[] {1})))
                            .code());
            assertEquals(200, client.get(base + "/8/pdf", editor).code());
        });

        verify(templates).list(OWNER, false);
        verify(templates).catalogue(OWNER);
        verify(templates).create(eq(OWNER), any(), anyInt());
        verify(templates).detail(OWNER, 8);
        verify(templates).update(eq(OWNER), eq(8), any(), anyInt());
        verify(templates).setArchived(eq(OWNER), eq(8), eq(true), anyInt());
        verify(templates).setArchived(eq(OWNER), eq(8), eq(false), anyInt());
        verify(generation).previewAssociationDraft(eq(OWNER), any(), isNull(), isNull());
        verify(imports).read(eq(new LetterImportService.Importer(OWNER, null)), any());
        verify(pdfs).upload(eq(OWNER), eq(8), any(), anyInt());
    }

    @Test
    void neitherAnotherAssociationRightNorAStationsTemplateRightOpensThem() {
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_MEMBER_READ));
            var station = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    403,
                    client.get(PREFIX + "/cluster/document-templates", reader).code());
            assertEquals(
                    403,
                    client.get(PREFIX + "/cluster/document-templates", station).code());
            assertEquals(
                    403,
                    client.put(PREFIX + "/cluster/document-templates/8", body("{}"), station)
                            .code());
        });
    }
}
