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
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentFilter;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.DocumentPage;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.MemberDocumentResponse;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.StoreQuery;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The document store over HTTP: each handler checks the station and hands the reading and writing to
 * the catalog.
 */
class DocumentRoutesTest {
    private static final MemberDocumentResponse VIEW = new MemberDocumentResponse(
            5,
            "Ausweis",
            "a.pdf",
            "application/pdf",
            1,
            false,
            false,
            false,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            false,
            List.of(),
            null);

    private DocumentCatalogService catalog;
    private DocumentService documentService;
    private DocumentAccessService access;
    private StationMemberService members;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        catalog = mock(DocumentCatalogService.class);
        documentService = mock(DocumentService.class);
        access = mock(DocumentAccessService.class);
        members = mock(StationMemberService.class);
        when(catalog.find(5)).thenReturn(Optional.of(document(5)));
        when(catalog.find(7)).thenReturn(Optional.of(document(7)));
        when(catalog.find(9))
                .thenReturn(Optional.of(new Document(
                        9,
                        4,
                        "Fremd",
                        "f.pdf",
                        "application/pdf",
                        1,
                        false,
                        false,
                        false,
                        null,
                        null,
                        Instant.EPOCH,
                        false)));
        when(catalog.view(any())).thenReturn(VIEW);
        when(catalog.setTags(any(), any())).thenReturn(VIEW);
        when(catalog.setMembers(any(), anyList())).thenReturn(VIEW);
        when(catalog.forMember(anyInt(), anyInt(), any(Boolean.class), any())).thenReturn(List.of(VIEW));
        when(catalog.page(anyInt(), any())).thenReturn(new DocumentPage(List.of(VIEW), 1));
        when(catalog.ids(anyInt(), any())).thenReturn(List.of(5, 7));
        when(catalog.tagNames(3)).thenReturn(List.of("Nachweis"));
        when(members.findById(11))
                .thenReturn(Optional.of(new StationMember(
                        11, 3, null, 1, false, null, "Mara", StationUserType.MEMBER, LocalDate.EPOCH)));
        harness = RouteHarness.serving(new DocumentRoutes(documentService, catalog, members, access));
    }

    private static Document document(int id) {
        return new Document(
                id, 3, "Akte", "a.pdf", "application/pdf", 1, false, false, false, null, null, Instant.EPOCH, false);
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

        verify(catalog).page(3, new StoreQuery(new DocumentFilter(List.of(11), "pass", true, false, false), 10, 1));
    }

    /**
     * The filter for documents of people who have left is one only a reader of member documents can use:
     * without that permission the store is the station's own paperwork, which names nobody who left.
     */
    @Test
    void theDocumentsOfPeopleWhoLeftAreListedForAReaderOfMemberDocuments() {
        harness.run((server, client) -> {
            var reader = harness.as(
                    TestSessions.member(3, StationPermission.DOCUMENT_READ, StationPermission.DOCUMENT_READ_MEMBER));
            assertEquals(
                    7,
                    json(client.get(PREFIX + "/documents/ids?departed=true", reader))
                            .get(1)
                            .asInt());
            client.get(
                    PREFIX + "/documents?departed=true",
                    harness.as(TestSessions.member(3, StationPermission.DOCUMENT_READ)));
        });

        verify(catalog).ids(3, new DocumentFilter(List.of(), null, true, false, true));
        verify(catalog).page(3, new StoreQuery(new DocumentFilter(List.of(), null, false, true, false), 24, 0));
    }

    /** A list of members that is not a list of numbers is a named refusal, not a server error. */
    @Test
    void membersThatAreNotNumbersAreRefusedByName() {
        harness.run((server, client) -> {
            var reader = harness.as(
                    TestSessions.member(3, StationPermission.DOCUMENT_READ, StationPermission.DOCUMENT_READ_MEMBER));
            var refused = client.get(PREFIX + "/documents?memberIds=11,zwei", reader);
            assertEquals(400, refused.code());
            assertEquals(DocumentRefusal.DOCUMENT_MEMBERS_NOT_NUMBERS, refusalOf(refused));
        });
    }

    /**
     * Pruning removes every document chosen in one go, each checked by the rule for removing one, and a
     * document of another station stops the whole of it.
     */
    @Test
    void severalDocumentsArePrunedInOneGo() {
        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(3, StationPermission.DOCUMENT_EDIT_MEMBER));
            assertEquals(
                    204,
                    client.post(PREFIX + "/documents/prune", body("{\"documentIds\": [5, 7, 5]}"), editor)
                            .code());
            assertEquals(
                    404,
                    client.post(PREFIX + "/documents/prune", body("{\"documentIds\": [5, 9]}"), editor)
                            .code());
        });

        verify(access, times(1)).requireMayDelete(any(), eq(document(5)));
        verify(access).requireMayDelete(any(), eq(document(7)));
        verify(documentService).remove(3, List.of(document(5), document(7)), DocumentDoor.STATION);
        verify(documentService, never()).remove(eq(3), eq(List.of(document(5))), any());
    }

    /** The file itself is answered through the one responder, under the name it was uploaded with. */
    @Test
    void theFileIsServedThroughTheService() {
        when(documentService.open(any(), eq(DocumentDoor.STATION))).thenReturn(Optional.of("Inhalt".getBytes()));
        harness.run((server, client) -> {
            var response = client.get(PREFIX + "/documents/5/content", harness.as(TestSessions.member(3)));
            assertEquals(200, response.code());
            assertEquals("Inhalt", response.body().string());
            assertTrue(String.valueOf(response.headers().get("Content-Disposition"))
                    .contains("a.pdf"));
        });
    }

    /**
     * A sealed version is served under the document's name with its number, after the same read check as the
     * document; a version the document lacks and a document of another station are refused.
     */
    @Test
    void aSealedVersionIsServedUnderItsNumber() {
        when(documentService.openVersion(any(), eq(2), eq(DocumentDoor.STATION)))
                .thenReturn(Optional.of("Fassung".getBytes()));
        when(documentService.openVersion(any(), eq(9), eq(DocumentDoor.STATION)))
                .thenThrow(DocumentRefusal.SEALED_VERSION_NOT_FOUND.raise());
        harness.run((server, client) -> {
            var reader = harness.as(TestSessions.member(3));

            var response = client.get(PREFIX + "/documents/5/versions/2/content", reader);
            assertEquals(200, response.code());
            assertEquals("Fassung", response.body().string());
            assertTrue(String.valueOf(response.headers().get("Content-Type")).contains("application/pdf"));
            assertTrue(String.valueOf(response.headers().get("Content-Disposition"))
                    .contains("a-v2.pdf"));

            var missing = client.get(PREFIX + "/documents/5/versions/9/content", reader);
            assertEquals(404, missing.code());
            assertEquals(DocumentRefusal.SEALED_VERSION_NOT_FOUND, refusalOf(missing));

            assertEquals(
                    404,
                    client.get(PREFIX + "/documents/9/versions/2/content", reader)
                            .code());
        });
        verify(access, times(2)).requireReadable(any(), any());
    }

    @Test
    void aVersionIsRefusedToAReaderWhoMayNotReadTheDocument() {
        doThrow(DocumentRefusal.DOCUMENT_NOT_YOURS_TO_READ.raise()).when(access).requireReadable(any(), any());
        harness.run((server, client) -> {
            var response = client.get(PREFIX + "/documents/5/versions/1/content", harness.as(TestSessions.member(3)));
            assertEquals(403, response.code());
        });
        verify(documentService, never()).openVersion(any(), anyInt(), any());
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
