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
import dev.chojo.ember.feature.generator.service.font.DocumentFontService;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService.DocumentFontsResponse;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final DocumentFontsResponse NONE = new DocumentFontsResponse(List.of(), List.of());
    private static final byte[] FONT = {0, 1, 0, 0};

    private DocumentFontService service;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        service = mock(DocumentFontService.class);
        when(service.list(any())).thenReturn(NONE);
        when(service.upload(any(), any(), any(), any(), anyBoolean(), anyInt())).thenReturn(NONE);
        when(service.delete(any(), anyInt(), anyInt())).thenReturn(NONE);
        harness = RouteHarness.serving(new DocumentFontRoutes(service));
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
