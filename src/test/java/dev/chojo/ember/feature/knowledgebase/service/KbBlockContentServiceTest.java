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
import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                noCellDescriptions(),
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
    void aPictureTheArticleSaysNothingAboutCarriesTheWordsOfItsFile() {
        var media = mock(MediaLibraryService.class);
        knowsPicture(media, "Das Wappen", "Am Tor der Wache");
        var describing = describingService(media);
        int id = createArticle("Text");
        try {
            describing.switchToRich(id);
            describing.saveBlocks(id, List.of(pictureRow()), member.id());
            var file = knowledgeBaseRepo.findFileById(id).orElseThrow();

            var raw = (CellConfig.ImageConfig)
                    describing.loadBlocks(file).getFirst().cells().getFirst().config();
            var described = (CellConfig.ImageConfig) describing
                    .describedBlocks(file)
                    .getFirst()
                    .cells()
                    .getFirst()
                    .config();
            assertNull(raw.altText(), "the editor keeps what the author wrote, which was nothing");
            assertEquals("Das Wappen", described.altText());
            assertEquals("Am Tor der Wache", described.description());

            String body = describing.getMarkdownContent(id).orElseThrow();
            assertTrue(body.contains("![Das Wappen]("), body);
            assertTrue(body.contains("Am Tor der Wache"), body);
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    @Test
    void thePrintedTextMarksTheCaptionWhileTheStoredTextKeepsItPlain() {
        var media = mock(MediaLibraryService.class);
        knowsPicture(media, "Das Wappen", "Am Tor & <neu>");
        var describing = describingService(media);
        int rich = createArticle("Text");
        int plain = createArticle("Nur *Text*");
        try {
            describing.switchToRich(rich);
            describing.saveBlocks(rich, List.of(pictureRow()), member.id());

            String printed = describing.printableMarkdown(
                    knowledgeBaseRepo.findFileById(rich).orElseThrow());
            assertTrue(printed.contains("<figcaption>Am Tor &amp; &lt;neu&gt;</figcaption>"), printed);
            assertFalse(describing.getMarkdownContent(rich).orElseThrow().contains("figcaption"));

            assertEquals(
                    "Nur *Text*",
                    describing.printableMarkdown(
                            knowledgeBaseRepo.findFileById(plain).orElseThrow()));
        } finally {
            knowledgeBaseRepo.purgeFile(rich);
            knowledgeBaseRepo.purgeFile(plain);
        }
    }

    @Test
    void theStoredTextCatchesUpWhenTheFileIsDescribedLaterWithoutANewVersion() {
        var media = mock(MediaLibraryService.class);
        knowsPicture(media, null, null);
        var describing = describingService(media);
        int id = createArticle("Text");
        try {
            describing.switchToRich(id);
            describing.saveBlocks(id, List.of(pictureRow()), member.id());
            int versions = describing.findVersions(id).size();

            knowsPicture(media, "Später beschrieben", null);

            assertTrue(describing.getMarkdownContent(id).orElseThrow().contains("![Später beschrieben]("));
            assertTrue(
                    knowledgeBaseRepo.readTextContent(id).orElseThrow().contains("![Später beschrieben]("),
                    "reading stores the fresh text, so search catches up too");
            assertEquals(versions, describing.findVersions(id).size(), "nobody edited the article");
        } finally {
            knowledgeBaseRepo.purgeFile(id);
        }
    }

    private static KbContentService describingService(MediaLibraryService media) {
        return new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                new CellDescriptions(media, (stationId, pageUid) -> Optional.empty()),
                stationRepo,
                mock(KbFileStorageService.class),
                new KbSearchService(knowledgeBaseRepo, stationRepo));
    }

    private static void knowsPicture(MediaLibraryService media, String alt, String description) {
        var file = new StationFile(
                1, 0, station.id(), "abc", "wappen.png", "image/png", 64, Instant.now(), alt, description, null);
        when(media.findByHash(station.id(), "abc")).thenReturn(Optional.of(file));
    }

    private static ContentBlockService.RowData pictureRow() {
        return row(
                CellContentType.IMAGE,
                "abc",
                new CellConfig.ImageConfig(null, null, null, null, null, null, null, null, null, null, null));
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
