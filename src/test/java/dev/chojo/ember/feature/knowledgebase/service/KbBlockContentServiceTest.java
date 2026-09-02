/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A knowledge-base article built from blocks. Everything it keeps - search, the PDF export, the
 * version history - keeps working because the stored body is a projection of the blocks and goes
 * through the same store a hand-written body does.
 */
class KbBlockContentServiceTest extends RepositoryTestBase {

    private static KbContentService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                mock(KbFileStorageService.class),
                new KbSearchService(knowledgeBaseRepo, stationRepo));
        station = stationRepo.create("KbBlockStation");
        account = accountRepo.create("kb-blocks@test.com", "Kb", "Blocks");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int createArticle(String body) {
        var file = knowledgeBaseRepo.createFile(
                station.id(), null, "Ausbildung", "", KbFileType.MARKDOWN, "text/markdown", 0, null, member.id());
        if (!body.isBlank()) service.storeText(file.id(), body);
        return file.id();
    }

    private static int createUpload() {
        return knowledgeBaseRepo
                .createFile(
                        station.id(), null, "Handbuch", "", KbFileType.PDF, "application/pdf", 10, null, member.id())
                .id();
    }

    private static ContentBlockService.RowData row(CellContentType type, String content, CellConfig config) {
        return new ContentBlockService.RowData(
                0, List.of(new ContentBlockService.CellData(0, 100.0, type, content, config)));
    }

    @Test
    void switchingKeepsWhatTheAuthorAlreadyWroteAndTheFileType() {
        int id = createArticle("Erst der Text");
        try {
            var switched = service.switchToRich(id).orElseThrow();
            assertEquals(ContentMode.RICH, switched.contentMode());
            assertNotNull(switched.containerId());
            assertEquals(KbFileType.MARKDOWN, switched.fileType(), "a rich article is still an article");

            var rows = service.loadBlocks(switched);
            assertEquals("Erst der Text", rows.getFirst().cells().getFirst().content());
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    @Test
    void onlyAMarkdownArticleCanBeBuiltFromBlocks() {
        int id = createUpload();
        try {
            assertThrows(BadRequestResponse.class, () -> service.switchToRich(id));
            assertTrue(service.switchToRich(99999).isEmpty());
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    @Test
    void switchingTwiceKeepsTheSameContainer() {
        int id = createArticle("Text");
        try {
            var first = service.switchToRich(id).orElseThrow();
            var second = service.switchToRich(id).orElseThrow();
            assertEquals(first.containerId(), second.containerId());
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    @Test
    void savingBlocksStoresTheProjectionAsTheBodyAndRecordsAVersion() {
        int id = createArticle("Alt");
        try {
            service.switchToRich(id);
            service.saveBlocks(id, List.of(row(CellContentType.MARKDOWN, "## Ablauf", CellConfig.EMPTY)), member.id());

            assertEquals(
                    "## Ablauf",
                    service.getMarkdownContent(id).orElseThrow(),
                    "the stored body is the projection, which is what search and the export read");
            assertFalse(
                    service.findVersions(id).isEmpty(),
                    "the projection goes through the same store, so history records it like any edit");
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    @Test
    void aPlainArticleHasNoBlocksToSave() {
        int id = createArticle("Nur Text");
        try {
            var rows = List.of(row(CellContentType.MARKDOWN, "x", CellConfig.EMPTY));
            assertThrows(BadRequestResponse.class, () -> service.saveBlocks(id, rows, member.id()));
            assertTrue(service.loadBlocks(knowledgeBaseRepo.findFileById(id).orElseThrow())
                    .isEmpty());
            assertTrue(service.saveBlocks(99999, rows, member.id()).isEmpty());
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    @Test
    void aPageOnlyBlockIsRefusedInAnArticle() {
        int id = createArticle("Text");
        try {
            service.switchToRich(id);
            var withheld =
                    List.of(row(CellContentType.ACHIEVEMENTS, "", new CellConfig.AchievementsConfig(null, null)));
            assertThrows(BadRequestResponse.class, () -> service.saveBlocks(id, withheld, member.id()));
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    @Test
    void deletingTheBlocksOfAnArticleIsSafeWhenThereAreNone() {
        int id = createArticle("Text");
        try {
            var plain = knowledgeBaseRepo.findFileById(id).orElseThrow();
            assertDoesNotThrow(() -> service.deleteBlocks(plain));

            var rich = service.switchToRich(id).orElseThrow();
            int containerId = rich.containerId();
            service.deleteBlocks(rich);
            assertTrue(contentContainerRepo.findById(containerId).isEmpty());
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }
}
