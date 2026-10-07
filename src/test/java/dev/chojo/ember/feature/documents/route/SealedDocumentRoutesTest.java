/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService.SealedFiling;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A sealed document over HTTP, through the real services and the database: it is listed as sealed with
 * its versions, and deleting it, pruning it or changing its members is refused by name.
 */
class SealedDocumentRoutesTest extends RepositoryTestBase {
    private static final byte[] SEALED = "%PDF-1.7 sealed for the routes".getBytes();

    private static Station station;
    private static RouteHarness harness;
    private static Document sealed;
    private static int member;

    @BeforeAll
    static void setup() {
        DocumentService documents = newDocumentService();
        var catalog = new DocumentCatalogService(memberDocumentRepo, documents);
        var access = new DocumentAccessService(memberDocumentRepo, documents, new GuardianPolicy(stationMemberRepo));
        harness =
                RouteHarness.serving(new DocumentRoutes(documents, catalog, mock(StationMemberService.class), access));
        station = stationRepo.create("Sealed Routes Station");
        var account = accountRepo.create("sealed-routes-" + System.nanoTime() + "@test.com", "Rou", "Te");
        member = stationMemberRepo.create(station.id(), account.id()).id();
        sealed = new SealedDocumentService(memberDocumentRepo, new SealedVersionRepository(), documents)
                .file(
                        station.id(),
                        new SealedFiling(
                                List.of(member), "Urkunde", "urkunde.pdf", false, Uploader.nobody(), List.of()),
                        SealedDocument.withoutTimestamp(SEALED));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static Consumer<Request.Builder> manager() {
        return harness.as(TestSessions.member(
                station.id(),
                StationPermission.DOCUMENT_READ,
                StationPermission.DOCUMENT_READ_MEMBER,
                StationPermission.DOCUMENT_EDIT,
                StationPermission.DOCUMENT_EDIT_MEMBER));
    }

    @Test
    void aSealedDocumentIsListedAsSealedWithItsVersion() {
        var listed = json(harness.request(client -> client.get(PREFIX + "/documents", manager())));

        var document = listed.path("documents").get(0);
        assertTrue(document.path("sealed").asBoolean());
        assertEquals(1, document.path("sealedVersions").size());
        assertEquals(1, document.path("sealedVersions").get(0).path("version").asInt());
        assertEquals(
                "BASELINE_B",
                document.path("sealedVersions").get(0).path("sealLevel").asString());
    }

    @Test
    void deletingASealedDocumentIsRefused() {
        var answer = harness.request(client -> client.delete(PREFIX + "/documents/" + sealed.id(), null, manager()));

        assertEquals(409, answer.code());
        assertEquals(DocumentRefusal.DOCUMENT_SEALED_NOT_REMOVABLE, refusalOf(answer));
        assertTrue(memberDocumentRepo.findById(sealed.id()).isPresent());
    }

    @Test
    void pruningASealedDocumentIsRefused() {
        var answer = harness.request(client ->
                client.post(PREFIX + "/documents/prune", body("{\"documentIds\": [" + sealed.id() + "]}"), manager()));

        assertEquals(DocumentRefusal.DOCUMENT_SEALED_NOT_REMOVABLE, refusalOf(answer));
        assertTrue(memberDocumentRepo.findById(sealed.id()).isPresent());
    }

    @Test
    void changingTheMembersOfASealedDocumentIsRefused() {
        var answer = harness.request(client ->
                client.put(PREFIX + "/documents/" + sealed.id() + "/members", body("{\"memberIds\": []}"), manager()));

        assertEquals(409, answer.code());
        assertEquals(DocumentRefusal.DOCUMENT_SEALED_MEMBERS_FIXED, refusalOf(answer));
        assertEquals(List.of(member), memberDocumentRepo.membersOf(sealed.id()));
    }

    @Test
    void aSealedDocumentIsNotOfferedForRemovingAll() {
        var ids = json(harness.request(client -> client.get(PREFIX + "/documents/ids", manager())));

        assertEquals(0, ids.size());
    }

    @Test
    void theSealedFileIsServedAsItWasFiled() {
        var answer =
                harness.request(client -> client.get(PREFIX + "/documents/" + sealed.id() + "/content", manager()));

        assertEquals(200, answer.code());
        assertEquals(new String(SEALED), answer.body().string());
    }
}
