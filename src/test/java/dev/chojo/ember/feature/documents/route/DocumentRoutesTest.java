/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.DocumentPage;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.MemberDocumentResponse;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.StoreQuery;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The document store over HTTP: each handler checks the station and hands the reading and writing to
 * the catalog.
 */
class DocumentRoutesTest {
    private static final MemberDocumentResponse VIEW = new MemberDocumentResponse(
            5, "Ausweis", "a.pdf", "application/pdf", 1, false, false, false, null, null, List.of(), List.of());

    private DocumentCatalogService catalog;
    private StationMemberService members;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        catalog = mock(DocumentCatalogService.class);
        members = mock(StationMemberService.class);
        var stations = mock(StationService.class);
        var document = mock(Document.class);
        when(document.id()).thenReturn(5);
        when(document.stationId()).thenReturn(3);
        when(catalog.find(5)).thenReturn(Optional.of(document));
        when(catalog.view(any())).thenReturn(VIEW);
        when(catalog.setTags(any(), any())).thenReturn(VIEW);
        when(catalog.setMembers(any(), anyList())).thenReturn(VIEW);
        when(catalog.forMember(anyInt(), anyInt(), any(Boolean.class))).thenReturn(List.of(VIEW));
        when(catalog.page(anyInt(), any())).thenReturn(new DocumentPage(List.of(VIEW), 1));
        when(catalog.tagNames(3)).thenReturn(List.of("Nachweis"));
        when(members.findById(11))
                .thenReturn(Optional.of(new StationMember(
                        11, 3, null, 1, false, null, "Mara", StationUserType.MEMBER, LocalDate.EPOCH)));
        when(stations.findDisabledModules(3)).thenReturn(Set.of());
        harness = RouteHarness.serving(new DocumentRoutes(
                mock(DocumentService.class), catalog, members, stations, mock(DocumentAccessService.class)));
    }

    @Test
    void theStoreItsWordsAndAMembersDocumentsAreRead() {
        harness.run((server, client) -> {
            var reader = harness.as(
                    TestSessions.member(3, StationPermission.DOCUMENT_READ, StationPermission.DOCUMENT_READ_MEMBER));
            assertEquals(
                    1,
                    json(client.get(PREFIX + "/documents?memberIds=11&search=pass&page=1&size=10", reader))
                            .path("total")
                            .asInt());
            assertEquals(
                    "Nachweis",
                    json(client.get(PREFIX + "/documents/tags", reader)).get(0).asString());
            assertEquals(
                    5,
                    json(client.get(PREFIX + "/station-members/11/documents", harness.as(TestSessions.member(3))))
                            .get(0)
                            .path("id")
                            .asInt());
        });

        verify(catalog).page(3, new StoreQuery(List.of(11), "pass", true, false, 10, 1));
    }

    @Test
    void wordsAndMembersAreSetOnADocumentOfTheStation() {
        harness.run((server, client) -> {
            var editor = harness.as(
                    TestSessions.member(3, StationPermission.DOCUMENT_EDIT, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(
                    200,
                    client.put(PREFIX + "/documents/5/tags", body("{\"tags\": [\"A\"]}"), editor)
                            .code());
            assertEquals(
                    200,
                    client.put(PREFIX + "/documents/5/members", body("{\"memberIds\": [11]}"), editor)
                            .code());
            assertEquals(
                    404,
                    client.put(PREFIX + "/documents/6/members", body("{\"memberIds\": []}"), editor)
                            .code());
        });

        verify(catalog).setTags(any(), eq(List.of("A")));
        verify(catalog).setMembers(any(), eq(List.of(11)));
    }
}
