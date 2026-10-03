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
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.service.font.BundledFont;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService.DocumentFontsResponse;
import dev.chojo.ember.feature.generator.service.font.EditorFontService;
import dev.chojo.ember.feature.generator.service.font.EditorFontService.EditorFontFile;
import dev.chojo.ember.feature.generator.service.font.FontSampleService;
import dev.chojo.ember.feature.generator.service.font.WebFontService;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.header;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The font routes over HTTP: each owner reaches its own fonts only, with the right it needs there, and
 * the owner comes from the session.
 */
class DocumentFontRoutesTest {
    private static final DocumentFontsResponse NONE =
            new DocumentFontsResponse(List.of(), List.of(), BundledFont.FAMILY, List.of());
    private static final byte[] FONT = {0, 1, 0, 0};

    private static final String PICTURE = "PNG";

    private DocumentFontService service;
    private FontSampleService samples;
    private EditorFontService files;
    private WebFontService webFonts;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        service = mock(DocumentFontService.class);
        samples = mock(FontSampleService.class);
        when(service.list(any())).thenReturn(NONE);
        when(service.upload(any(), any(), any(), any(), anyBoolean(), anyInt())).thenReturn(NONE);
        when(service.delete(any(), anyInt(), anyInt())).thenReturn(NONE);
        when(samples.sample(any(), any(), any())).thenReturn(PICTURE.getBytes(StandardCharsets.US_ASCII));
        when(samples.sample(any(), eq("Fehlt"), any())).thenThrow(DocumentRefusal.DOCUMENT_FONT_SAMPLE_UNKNOWN.raise());
        files = mock(EditorFontService.class);
        when(files.file(any(), any(), any())).thenReturn(new EditorFontFile(FONT, "font/ttf"));
        when(files.file(any(), eq("Fehlt"), any())).thenThrow(DocumentRefusal.DOCUMENT_FONT_FILE_UNKNOWN.raise());
        webFonts = mock(WebFontService.class);
        when(webFonts.upload(any(), anyInt(), any(), anyBoolean(), anyInt())).thenReturn(NONE);
        when(webFonts.remove(any(), anyInt(), anyInt())).thenReturn(NONE);
        harness = RouteHarness.serving(new DocumentFontRoutes(service, samples, files, webFonts));
    }

    /** Every owner gives the styles of its own fonts a web version and takes it away again, with its right. */
    @Test
    void eachOwnerKeepsTheWebVersionsOfItsFonts() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    200,
                    client.request(
                                    PREFIX + "/document-fonts/5/web",
                                    editor.andThen(
                                            TestUploads.multipart("schrift.woff2", FONT, Map.of("confirmed", "true"))))
                            .code());
            assertEquals(
                    200,
                    client.delete(PREFIX + "/document-fonts/5/web", null, editor)
                            .code());
            var filer = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(
                    403,
                    client.delete(PREFIX + "/document-fonts/5/web", null, filer).code());

            var association =
                    harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    200,
                    client.request(
                                    PREFIX + "/cluster/document-fonts/6/web",
                                    association.andThen(TestUploads.multipart("schrift.woff2", FONT, Map.of())))
                            .code());
            assertEquals(
                    200,
                    client.delete(PREFIX + "/cluster/document-fonts/6/web", null, association)
                            .code());

            var admin = harness.as(TestSessions.administrator());
            assertEquals(
                    200,
                    client.request(
                                    PREFIX + "/admin/document-fonts/4/web",
                                    admin.andThen(
                                            TestUploads.multipart("schrift.woff2", FONT, Map.of("confirmed", "true"))))
                            .code());
            assertEquals(
                    200,
                    client.delete(PREFIX + "/admin/document-fonts/4/web", null, admin)
                            .code());
            assertEquals(
                    403,
                    client.delete(PREFIX + "/admin/document-fonts/4/web", null, editor)
                            .code());
        });

        verify(webFonts).upload(eq(new Owner.Station(3)), eq(5), any(), eq(true), anyInt());
        verify(webFonts).remove(eq(new Owner.Station(3)), eq(5), anyInt());
        verify(webFonts).upload(eq(new Owner.Association(7)), eq(6), any(), eq(false), anyInt());
        verify(webFonts).remove(eq(new Owner.Association(7)), eq(6), anyInt());
        verify(webFonts).upload(eq(new Owner.Instance()), eq(4), any(), eq(true), anyInt());
        verify(webFonts).remove(eq(new Owner.Instance()), eq(4), anyInt());
    }

    /**
     * The template editor of a station and of an association loads the files of what their templates
     * reach, with the right to edit templates; the file is shown inline and kept privately for a few
     * minutes, per station and association it was asked for. The instance has no template editor and
     * no such route.
     */
    @Test
    void eachTemplateEditorLoadsTheFilesOfWhatItReaches() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            var file = client.get(PREFIX + "/document-fonts/file?family=Hausschrift&style=BOLD&v=ab", editor);
            assertEquals(200, file.code());
            assertTrue(header(file, "Content-Type").startsWith("font/ttf"));
            assertEquals("inline", header(file, "Content-Disposition"));
            assertEquals("private, max-age=300", header(file, "Cache-Control"));
            assertEquals("X-Station-Id, X-Cluster-Id", header(file, "Vary"));
            assertEquals(new String(FONT, StandardCharsets.UTF_8), file.body().string());
            assertEquals(
                    DocumentRefusal.DOCUMENT_FONT_FILE_UNKNOWN,
                    refusalOf(client.get(PREFIX + "/document-fonts/file?family=Fehlt", editor)));
            assertEquals(
                    DocumentRefusal.DOCUMENT_FONT_STYLE_UNKNOWN,
                    refusalOf(client.get(PREFIX + "/document-fonts/file?style=FETT", editor)));
            var filer = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(403, client.get(PREFIX + "/document-fonts/file", filer).code());

            var association =
                    harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    200,
                    client.get(PREFIX + "/cluster/document-fonts/file?style=ITALIC", association)
                            .code());
            var reader = harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_MEMBER_READ));
            assertEquals(
                    403,
                    client.get(PREFIX + "/cluster/document-fonts/file", reader).code());
            assertEquals(
                    404,
                    client.get(PREFIX + "/admin/document-fonts/file", harness.as(TestSessions.administrator()))
                            .code());
        });

        verify(files).file(new Owner.Station(3), "Hausschrift", FontStyle.BOLD);
        verify(files).file(new Owner.Association(7), null, FontStyle.ITALIC);
    }

    /**
     * Every owner draws samples of what its own templates reach, with the right it keeps its fonts by;
     * the picture is kept by the browser for a week, per station and association it was asked for.
     */
    @Test
    void eachOwnerDrawsSamplesOfWhatItReaches() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            var sample = client.get(PREFIX + "/document-fonts/sample?family=Hausschrift&style=BOLD&v=ab", editor);
            assertEquals(200, sample.code());
            assertTrue(header(sample, "Content-Type").startsWith("image/png"));
            assertEquals("private, max-age=604800", header(sample, "Cache-Control"));
            assertEquals("X-Station-Id, X-Cluster-Id", header(sample, "Vary"));
            assertEquals(PICTURE, sample.body().string());
            assertEquals(
                    DocumentRefusal.DOCUMENT_FONT_SAMPLE_UNKNOWN,
                    refusalOf(client.get(PREFIX + "/document-fonts/sample?family=Fehlt", editor)));
            assertEquals(
                    DocumentRefusal.DOCUMENT_FONT_STYLE_UNKNOWN,
                    refusalOf(client.get(PREFIX + "/document-fonts/sample?style=FETT", editor)));
            var filer = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(
                    403, client.get(PREFIX + "/document-fonts/sample", filer).code());

            var association =
                    harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    200,
                    client.get(PREFIX + "/cluster/document-fonts/sample?style=ITALIC", association)
                            .code());
            assertEquals(
                    200,
                    client.get(
                                    PREFIX + "/admin/document-fonts/sample?family=Liberation%20Mono",
                                    harness.as(TestSessions.administrator()))
                            .code());
            assertEquals(
                    403,
                    client.get(PREFIX + "/admin/document-fonts/sample", editor).code());
        });

        verify(samples).sample(new Owner.Station(3), "Hausschrift", FontStyle.BOLD);
        verify(samples).sample(new Owner.Association(7), null, FontStyle.ITALIC);
        verify(samples).sample(new Owner.Instance(), "Liberation Mono", FontStyle.REGULAR);
    }

    private static Map<String, String> form(String style) {
        return Map.of("family", "Hausschrift", "style", style, "confirmed", "true");
    }

    @Test
    void anEditorOfTemplatesKeepsTheStationsFonts() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            assertEquals(200, client.get(PREFIX + "/document-fonts", editor).code());
            assertEquals(
                    200,
                    client.request(
                                    PREFIX + "/document-fonts",
                                    editor.andThen(TestUploads.multipart("schrift.ttf", FONT, form("BOLD"))))
                            .code());
            assertEquals(
                    DocumentRefusal.DOCUMENT_FONT_STYLE_UNKNOWN,
                    refusalOf(client.request(
                            PREFIX + "/document-fonts",
                            editor.andThen(TestUploads.multipart("schrift.ttf", FONT, form("FETT"))))));
            assertEquals(
                    200,
                    client.delete(PREFIX + "/document-fonts/5", null, editor).code());
            var filer = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(403, client.get(PREFIX + "/document-fonts", filer).code());
        });

        var owner = new Owner.Station(3);
        verify(service).list(owner);
        verify(service).upload(eq(owner), any(), eq("Hausschrift"), eq(FontStyle.BOLD), eq(true), anyInt());
        verify(service).delete(eq(owner), eq(5), anyInt());
    }

    @Test
    void anAssociationKeepsItsOwnFonts() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    200, client.get(PREFIX + "/cluster/document-fonts", editor).code());
            assertEquals(
                    200,
                    client.request(
                                    PREFIX + "/cluster/document-fonts",
                                    editor.andThen(TestUploads.multipart("schrift.ttf", FONT, form(""))))
                            .code());
            assertEquals(
                    200,
                    client.delete(PREFIX + "/cluster/document-fonts/6", null, editor)
                            .code());
            var reader = harness.as(TestSessions.clusterMember(7, ClusterPermission.CLUSTER_MEMBER_READ));
            assertEquals(
                    403, client.get(PREFIX + "/cluster/document-fonts", reader).code());
        });

        var owner = new Owner.Association(7);
        verify(service).list(owner);
        verify(service).upload(eq(owner), any(), eq("Hausschrift"), eq(FontStyle.REGULAR), eq(true), anyInt());
        verify(service).delete(eq(owner), eq(6), anyInt());
    }

    @Test
    void theAdministratorKeepsTheInstancesFonts() {
        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            assertEquals(
                    200, client.get(PREFIX + "/admin/document-fonts", admin).code());
            assertEquals(
                    200,
                    client.request(
                                    PREFIX + "/admin/document-fonts",
                                    admin.andThen(TestUploads.multipart("schrift.ttf", FONT, form("ITALIC"))))
                            .code());
            assertEquals(
                    200,
                    client.delete(PREFIX + "/admin/document-fonts/4", null, admin)
                            .code());
            var member = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_TEMPLATE_EDIT));
            assertEquals(
                    403, client.get(PREFIX + "/admin/document-fonts", member).code());
        });

        var owner = new Owner.Instance();
        verify(service).list(owner);
        verify(service).upload(eq(owner), any(), eq("Hausschrift"), eq(FontStyle.ITALIC), eq(true), anyInt());
        verify(service).delete(eq(owner), eq(4), anyInt());
    }
}
