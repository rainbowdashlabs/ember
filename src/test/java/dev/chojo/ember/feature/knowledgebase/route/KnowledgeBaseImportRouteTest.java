/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.KnowledgeBaseRefusal;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbBrowseService;
import dev.chojo.ember.feature.knowledgebase.service.KbBulkService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KbMoveService;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reading a document into the wiki goes by what the file is: a Word document is read whatever it is
 * called, and an old binary Word file is refused as a kind the wiki cannot read.
 */
class KnowledgeBaseImportRouteTest {

    @Test
    void aDocumentIsReadForWhatItIsAndTheOldWordFormatIsRefused() throws IOException {
        byte[] word;
        try (var in = getClass().getResourceAsStream("/generator/certificate.docx")) {
            word = Objects.requireNonNull(in, "the fixture").readAllBytes();
        }
        byte[] oldWord = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};
        var service = mock(KnowledgeBaseService.class);
        when(service.createMarkdownFile(anyInt(), any(), any(), any(), any(), anyInt()))
                .thenReturn(new KbFile(
                        7,
                        3,
                        null,
                        "notiz",
                        "",
                        KbFileType.MARKDOWN,
                        "text/markdown",
                        1,
                        null,
                        null,
                        null,
                        0,
                        1,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        null,
                        null,
                        RestrictionMode.AND,
                        false,
                        null,
                        ContentMode.SIMPLE,
                        null));
        var harness = RouteHarness.serving(new KnowledgeBaseRoutes(
                service,
                mock(KbContentService.class),
                mock(KbSearchService.class),
                mock(KbAccessService.class),
                mock(KbPresentationService.class),
                mock(KbAuthorNameService.class),
                mock(KnowledgeBaseFederationService.class),
                mock(KbIconService.class),
                mock(KbImageService.class),
                mock(KbFilePictureService.class),
                mock(KbPdfExportService.class),
                mock(KbMoveService.class),
                mock(KbBulkService.class),
                mock(KbTrashService.class),
                mock(KbBrowseService.class)));
        var editor = harness.as(TestSessions.member(3, StationPermission.KNOWLEDGE_EDIT));

        harness.run((server, client) -> {
            var read = client.request(
                    PREFIX + "/kb/files/import-document", editor.andThen(TestUploads.multipart("notiz.odt", word)));
            assertEquals(200, read.code());
            assertEquals(
                    KnowledgeBaseRefusal.KB_IMPORT_KIND_UNKNOWN,
                    refusalOf(client.request(
                            PREFIX + "/kb/files/import-document",
                            editor.andThen(TestUploads.multipart("alt.doc", oldWord)))));
        });

        verify(service)
                .createMarkdownFile(
                        eq(3),
                        any(),
                        eq("notiz"),
                        eq(""),
                        argThat(markdown -> markdown.contains("Bescheinigung")),
                        anyInt());
        verify(service, never()).createMarkdownFile(anyInt(), any(), eq("alt"), any(), any(), anyInt());
    }
}
