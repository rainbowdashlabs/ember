/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.feature.system.repository.DatabaseServerRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The search indexes are built again when the database has moved to another major version, and not
 * on every start.
 */
@Tag("database")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SearchIndexRebuildServiceTest extends RepositoryTestBase {

    @TempDir
    static Path storageRoot;

    private static SearchIndexRebuildService service;
    private static DocumentService documents;
    private static String currentMajor;
    private static Station station;
    private static Account account;
    private static int memberId;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        documents = new DocumentService(memberDocumentRepo, storage, new ImageVariantService(storage), stationRepo);
        var server = new DatabaseServerRepository();
        currentMajor = String.valueOf(server.majorVersion());
        service = new SearchIndexRebuildService(
                applicationSettingRepo,
                server,
                new KbSearchService(knowledgeBaseRepo, stationRepo),
                documents,
                boardTicketRepo);
        station = stationRepo.create("Search Index Rebuild Station");
        account = accountRepo.create("search-rebuild@test.com", "Search", "Rebuild");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static Document storeText(String title, String text) {
        return documents.store(
                station.id(),
                List.of(memberId),
                title,
                title.toLowerCase() + ".txt",
                "text/plain",
                text.getBytes(StandardCharsets.UTF_8),
                false,
                false,
                memberId,
                List.of());
    }

    private static boolean lacksSourceText(Document document) {
        return memberDocumentRepo.findWithoutSourceText().stream().anyMatch(found -> found.id() == document.id());
    }

    /** A document stored now keeps the text its index was built from. */
    @Test
    @Order(1)
    void aStoredDocumentKeepsItsText() {
        var document = storeText("Atemschutz", "Belastungsuebung im Atemschutz");

        assertFalse(lacksSourceText(document));
    }

    /**
     * An instance that never recorded a version cannot tell whether it was upgraded before the check
     * existed, so it builds everything once, and a document indexed before its text was kept is read
     * back out of storage for it.
     */
    @Test
    @Order(2)
    void anUnrecordedVersionRebuildsAndReadsOldDocumentsBack() {
        var document = storeText("Kettensaege", "Unterweisung an der Kettensaege");
        query("UPDATE member_document_search SET source_text = NULL WHERE document_id = :id;")
                .single(call().bind("id", document.id()))
                .update();
        applicationSettingRepo.set(SearchIndexRebuildService.BUILT_ON_KEY, "");

        assertTrue(lacksSourceText(document));
        assertTrue(service.rebuildIfNeeded());
        assertFalse(lacksSourceText(document), "the file should have been read back for its text");
        assertEquals(
                currentMajor,
                applicationSettingRepo
                        .get(SearchIndexRebuildService.BUILT_ON_KEY)
                        .orElseThrow());
    }

    /** A start on the version the indexes were built on leaves them alone. */
    @Test
    @Order(3)
    void theSameVersionRebuildsNothing() {
        applicationSettingRepo.set(SearchIndexRebuildService.BUILT_ON_KEY, currentMajor);

        assertFalse(service.rebuildIfNeeded());
    }

    /** A database moved to another major version gets its indexes built again, once. */
    @Test
    @Order(4)
    void anotherVersionRebuildsOnce() {
        applicationSettingRepo.set(
                SearchIndexRebuildService.BUILT_ON_KEY, String.valueOf(Integer.parseInt(currentMajor) - 1));

        assertTrue(service.rebuildIfNeeded());
        assertFalse(service.rebuildIfNeeded(), "the second start on the new version should do nothing");
    }
}
