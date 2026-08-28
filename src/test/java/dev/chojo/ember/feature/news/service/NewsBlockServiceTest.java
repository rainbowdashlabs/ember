/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A news entry built from blocks, and the projection that keeps everything downstream working.
 */
class NewsBlockServiceTest extends RepositoryTestBase {
    private static NewsService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new NewsService(
                newsRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                restrictionService,
                new DomainEventBus(Set.of()),
                stationMemberRepo,
                memberLookupService,
                accountRepo);
        station = stationRepo.create("NewsBlockStation");
        account = accountRepo.create("news-blocks@test.com", "News", "Blocks");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int createEntry(String markdown) {
        return newsRepo.create(
                        station.id(),
                        "Übungsbericht",
                        markdown,
                        "<p>" + markdown + "</p>",
                        new MemberIdentity(station.uid(), member.uid()))
                .id();
    }

    private static ContentBlockService.RowData row(CellContentType type, String content, CellConfig config) {
        return new ContentBlockService.RowData(
                0, List.of(new ContentBlockService.CellData(0, 100.0, type, content, config)));
    }

    @Test
    void switchingToRichKeepsWhatTheAuthorAlreadyWrote() {
        int id = createEntry("Erst der Text");
        try {
            var switched = service.switchToRich(id).orElseThrow();
            assertEquals(ContentMode.RICH, switched.contentMode());
            assertNotNull(switched.containerId());

            var rows = service.loadBlocks(switched);
            assertEquals(1, rows.size());
            assertEquals(
                    "Erst der Text",
                    rows.getFirst().cells().getFirst().content(),
                    "nothing is parsed, so nothing can be lost");
        } finally {
            service.delete(id);
        }
    }

    @Test
    void switchingIsIdempotentAndOneWay() {
        int id = createEntry("Text");
        try {
            var first = service.switchToRich(id).orElseThrow();
            var second = service.switchToRich(id).orElseThrow();
            assertEquals(first.containerId(), second.containerId(), "asking twice does not make a second container");
            assertTrue(service.switchToRich(99999).isEmpty());
        } finally {
            service.delete(id);
        }
    }

    @Test
    void anEmptyEntryBecomesRichWithNoBlockAtAll() {
        int id = createEntry("");
        try {
            var switched = service.switchToRich(id).orElseThrow();
            assertTrue(service.loadBlocks(switched).isEmpty());
        } finally {
            service.delete(id);
        }
    }

    @Test
    void savingBlocksRewritesTheStoredTextFromThem() {
        int id = createEntry("Alt");
        try {
            service.switchToRich(id);
            var saved = service.saveBlocks(
                            id,
                            List.of(
                                    row(CellContentType.MARKDOWN, "## Ablauf", CellConfig.EMPTY),
                                    row(
                                            CellContentType.IMAGE,
                                            "abc",
                                            new CellConfig.ImageConfig(
                                                    null,
                                                    "Fahrzeug",
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null))))
                    .orElseThrow();

            assertTrue(saved.contentMarkdown().contains("## Ablauf"));
            assertTrue(
                    saved.contentMarkdown().contains("![Fahrzeug](/api/v1/public/media/" + station.uid() + "/abc)"),
                    "the projection addresses media the way a reader anywhere can load it");
            assertFalse(saved.contentMarkdown().contains("Alt"), "the projection replaces what was there");
            assertTrue(saved.contentHtml().contains("<h2"), "the stored html is rendered from the projection");
        } finally {
            service.delete(id);
        }
    }

    @Test
    void reorderingAloneStillRewritesTheProjection() {
        int id = createEntry("Alt");
        try {
            service.switchToRich(id);
            service.saveBlocks(
                    id,
                    List.of(
                            new ContentBlockService.RowData(
                                    0,
                                    List.of(new ContentBlockService.CellData(
                                            0, 100.0, CellContentType.MARKDOWN, "erst", CellConfig.EMPTY))),
                            new ContentBlockService.RowData(
                                    1,
                                    List.of(new ContentBlockService.CellData(
                                            0, 100.0, CellContentType.MARKDOWN, "dann", CellConfig.EMPTY)))));

            var reordered = service.saveBlocks(
                            id,
                            List.of(
                                    new ContentBlockService.RowData(
                                            0,
                                            List.of(new ContentBlockService.CellData(
                                                    0, 100.0, CellContentType.MARKDOWN, "dann", CellConfig.EMPTY))),
                                    new ContentBlockService.RowData(
                                            1,
                                            List.of(new ContentBlockService.CellData(
                                                    0, 100.0, CellContentType.MARKDOWN, "erst", CellConfig.EMPTY)))))
                    .orElseThrow();
            assertEquals(
                    "dann\n\nerst",
                    reordered.contentMarkdown(),
                    "a stale projection means a stale search summary and a stale feed");
        } finally {
            service.delete(id);
        }
    }

    @Test
    void aPageOnlyBlockIsRefusedInAnEntry() {
        int id = createEntry("Text");
        try {
            service.switchToRich(id);
            var withheld = List.of(row(CellContentType.BLOG_SIGNUP, "", new CellConfig.BlogSignupConfig("A", "B")));
            assertThrows(BadRequestResponse.class, () -> service.saveBlocks(id, withheld));
        } finally {
            service.delete(id);
        }
    }

    @Test
    void aPlainEntryHasNoBlocksToSave() {
        int id = createEntry("Nur Text");
        try {
            var rows = List.of(row(CellContentType.MARKDOWN, "x", CellConfig.EMPTY));
            assertThrows(BadRequestResponse.class, () -> service.saveBlocks(id, rows));
            assertTrue(service.loadBlocks(newsRepo.findById(id).orElseThrow()).isEmpty());
            assertTrue(service.saveBlocks(99999, rows).isEmpty());
        } finally {
            service.delete(id);
        }
    }

    /**
     * A system entry is read in every station, so the blocks it is built from belong to none of
     * them and neither do the pictures in them. Hanging the container off the station the entry
     * reads as would have been an owner no station is.
     */
    @Test
    void aSystemEntryIsBuiltFromBlocksThatBelongToNoStation() {
        var entry =
                service.createSystem("Wartungsarbeiten", "Am Freitag kurz nicht erreichbar.", List.of(), true, false);
        try {
            var switched = service.switchToRich(entry.id()).orElseThrow();
            assertNull(
                    contentContainerRepo
                            .findById(switched.containerId())
                            .orElseThrow()
                            .stationId(),
                    "an entry every station reads cannot hang off one of them");

            var saved = service.saveBlocks(
                            entry.id(),
                            List.of(row(
                                    CellContentType.IMAGE,
                                    "abc",
                                    new CellConfig.ImageConfig(
                                            null, "Plan", null, null, null, null, null, null, null, null, null))))
                    .orElseThrow();
            assertTrue(
                    saved.contentMarkdown().contains("![Plan](/api/v1/public/media/instance/abc)"),
                    "the picture comes out of the instance library, which every station is served");
        } finally {
            service.delete(entry.id());
        }
    }

    @Test
    void deletingAnEntryTakesItsBlocksWithIt() {
        int id = createEntry("Text");
        var switched = service.switchToRich(id).orElseThrow();
        int containerId = switched.containerId();

        assertTrue(service.delete(id));
        assertTrue(
                contentContainerRepo.findById(containerId).isEmpty(),
                "the container is the owned side, so nothing else would ever clean it up");
    }
}
