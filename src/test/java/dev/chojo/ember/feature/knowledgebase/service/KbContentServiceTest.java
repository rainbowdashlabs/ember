/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KbContentServiceTest extends RepositoryTestBase {
    private static KbContentService service;
    private static KbFileStorageService fileStorage;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        fileStorage = mock(KbFileStorageService.class);
        service = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                fileStorage,
                new KbSearchService(knowledgeBaseRepo, stationRepo));
        station = stationRepo.create("KbContentStation");
        account = accountRepo.create("kb-content@test.com", "Kb", "ContentTester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int createFile(String name) {
        return knowledgeBaseRepo
                .createFile(station.id(), null, name, "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id())
                .id();
    }

    /**
     * A freshly created markdown file carries its body and a first version holding that same body
     * in full, which is what every later diff is replayed on top of.
     */
    @Test
    void initialiseMarkdownStoresTheFirstVersionInFull() {
        int fileId = createFile("Initialised");
        service.initialiseMarkdown(fileId, "# Guide", member.id());

        assertEquals("# Guide", service.getMarkdownContent(fileId).orElseThrow());
        var versions = service.findVersions(fileId);
        assertEquals(1, versions.size());
        assertTrue(versions.getFirst().isFull());
        assertTrue(service.findVersion(fileId, 1).isPresent());
        assertTrue(service.findVersion(fileId, 99).isEmpty());

        knowledgeBaseRepo.deleteFile(fileId);
    }

    /**
     * Every edit adds a version, and an old version can be read back by replaying the patches that
     * followed the first one.
     */
    @Test
    void everyEditIsReconstructableFromItsVersion() {
        int fileId = createFile("Edited");
        service.initialiseMarkdown(fileId, "# Version 1", member.id());
        service.updateMarkdownContent(fileId, "# Version 2", member.id());
        service.updateMarkdownContent(fileId, "# Version 3", member.id());

        assertEquals("# Version 3", service.getMarkdownContent(fileId).orElseThrow());
        assertEquals("# Version 1", service.reconstructVersion(fileId, 1).orElseThrow());
        assertEquals("# Version 2", service.reconstructVersion(fileId, 2).orElseThrow());
        assertEquals(3, service.findVersions(fileId).size());

        knowledgeBaseRepo.deleteFile(fileId);
    }

    /**
     * Reverting restores an earlier body as a new version, so the history keeps every step rather
     * than rewriting it.
     */
    @Test
    void revertingRestoresAnEarlierBodyAsANewVersion() {
        int fileId = createFile("Reverted");
        service.initialiseMarkdown(fileId, "# Original", member.id());
        service.updateMarkdownContent(fileId, "# Changed", member.id());

        service.revertToVersion(fileId, 1, member.id());

        assertEquals("# Original", service.getMarkdownContent(fileId).orElseThrow());
        assertEquals(3, service.findVersions(fileId).size());

        knowledgeBaseRepo.deleteFile(fileId);
    }

    @Test
    void revertingAFileWithoutVersionsChangesNothing() {
        service.revertToVersion(99999, 1, member.id());
        assertTrue(service.reconstructVersion(99999, 1).isEmpty());
    }

    /**
     * Rendered markdown is sanitised, so a body carrying a script tag cannot smuggle it into the
     * page that shows the file.
     */
    @Test
    void renderedMarkdownIsSanitised() {
        String html = service.renderMarkdown("# Hello\n\n<script>alert(1)</script>\n\nWorld");
        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("Hello"));
        assertFalse(html.contains("<script"));
    }

    @Test
    void storeTextKeepsAnEmptyBody() {
        int fileId = createFile("Empty");
        service.storeText(fileId, "");
        assertEquals("", service.getMarkdownContent(fileId).orElseThrow());
        knowledgeBaseRepo.deleteFile(fileId);
    }

    /**
     * Text that could not be extracted from a binary is not stored as an empty body, so the file
     * keeps reporting that it has no readable content.
     */
    @Test
    void extractedTextIsOnlyStoredWhenSomethingWasExtracted() {
        int fileId = createFile("Extracted");

        service.storeExtractedText(fileId, "   ");
        assertTrue(service.getMarkdownContent(fileId).isEmpty());

        service.storeExtractedText(fileId, null);
        assertTrue(service.getMarkdownContent(fileId).isEmpty());

        service.storeExtractedText(fileId, "extracted body");
        assertEquals("extracted body", service.getMarkdownContent(fileId).orElseThrow());

        knowledgeBaseRepo.deleteFile(fileId);
    }

    @Test
    void binaryContentIsReadFromTheOwningStation() {
        int fileId = createFile("Binary");
        byte[] payload = "binary".getBytes(StandardCharsets.UTF_8);
        when(fileStorage.read(station.id(), fileId))
                .thenReturn(Optional.of(new KbFileStorageService.FileData(payload, "application/octet-stream")));

        assertArrayEquals(payload, service.getFileContent(fileId).orElseThrow());
        assertEquals(
                "application/octet-stream", service.getFileContentType(fileId).orElseThrow());

        knowledgeBaseRepo.deleteFile(fileId);
    }

    @Test
    void binaryContentOfAnUnknownFileIsEmpty() {
        assertTrue(service.getFileContent(99999).isEmpty());
        assertTrue(service.getFileContentType(99999).isEmpty());
    }
}
