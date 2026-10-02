/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.NewsRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.content.BlockReferenceTestBase;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.service.ContentBlockService.Scope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A news or event block is saved only naming what every reader of its content may see: on a page
 * what is public, in a news or wiki article what every member may see. Anything else is refused with
 * a named refusal and nothing is written, one level down in nested rows as much as at the top.
 */
class BlockReferenceSaveTest extends BlockReferenceTestBase {
    private static ContentBlockService blocks;

    @BeforeAll
    static void setup() {
        blocks = contentBlocks();
    }

    private static ContentBlockService.RowData row(CellContentType type, CellConfig config) {
        return new ContentBlockService.RowData(
                0, List.of(new ContentBlockService.CellData(0, 100.0, type, "", config)));
    }

    private static ContentBlockService.RowData teaser(UUID newsUid) {
        return row(
                CellContentType.NEWS_TEASER,
                CellConfig.parse(
                        CellContentType.NEWS_TEASER,
                        CellConfig.MAPPER.readTree("{\"newsUid\":\"%s\"}".formatted(newsUid))));
    }

    private static ContentBlockService.RowData featured(UUID eventUid) {
        return row(
                CellContentType.FEATURED_EVENT,
                CellConfig.parse(
                        CellContentType.FEATURED_EVENT,
                        CellConfig.MAPPER.readTree(
                                "{\"eventUid\":\"%s\",\"date\":\"2027-07-01\"}".formatted(eventUid))));
    }

    private static ContentBlockService.RowData nested(String contentType, String config) {
        return row(
                CellContentType.NESTED_ROWS,
                CellConfig.parse(
                        CellContentType.NESTED_ROWS,
                        CellConfig.MAPPER.readTree("{\"rows\":[{\"cells\":[{\"contentType\":\"%s\",\"config\":%s}]}]}"
                                .formatted(contentType, config))));
    }

    private static void saved(Scope scope, ContentBlockService.RowData row) {
        var container = blocks.create(station.id());
        try {
            blocks.save(container.id(), List.of(row), scope);
            assertEquals(1, blocks.loadRows(container.id()).size());
        } finally {
            blocks.delete(container.id());
        }
    }

    private static void refused(Refusal expected, Scope scope, ContentBlockService.RowData row, String why) {
        var container = blocks.create(station.id());
        try {
            Executable save = () -> blocks.save(container.id(), List.of(row), scope);
            assertEquals(
                    expected, assertThrows(RefusalResponse.class, save, why).refusal(), why);
            assertTrue(blocks.loadRows(container.id()).isEmpty(), why);
        } finally {
            blocks.delete(container.id());
        }
    }

    @Test
    void aPageKeepsANewsBlockNamingAnEntryOnThePublicBlog() {
        var container = blocks.create(station.id());
        try {
            blocks.save(container.id(), List.of(teaser(publicNews.publicUid())), Scope.PAGE);

            var stored = assertInstanceOf(
                    CellConfig.NewsTeaserConfig.class,
                    blocks.loadRows(container.id())
                            .getFirst()
                            .cells()
                            .getFirst()
                            .config());
            assertEquals(publicNews.publicUid().toString(), stored.newsUid());
        } finally {
            blocks.delete(container.id());
        }
    }

    @Test
    void anArticleKeepsAnEventBlockNamingAnInternalAppointment() {
        var container = blocks.create(station.id());
        try {
            blocks.save(container.id(), List.of(featured(uidOf(internalEvent))), Scope.ARTICLE);

            var stored = assertInstanceOf(
                    CellConfig.FeaturedEventConfig.class,
                    blocks.loadRows(container.id())
                            .getFirst()
                            .cells()
                            .getFirst()
                            .config());
            assertEquals(uidOf(internalEvent).toString(), stored.eventUid());
            assertEquals("2027-07-01", stored.date());
        } finally {
            blocks.delete(container.id());
        }
    }

    @Test
    void anArticleNamesWhatEveryMemberMaySeeInternalOnesIncluded() {
        assertDoesNotThrow(() -> saved(Scope.ARTICLE, teaser(publicNews.publicUid())));
        assertDoesNotThrow(() -> saved(Scope.ARTICLE, teaser(internalNews.publicUid())));
        assertDoesNotThrow(() -> saved(Scope.ARTICLE, featured(uidOf(publicEvent))));
        assertDoesNotThrow(() -> saved(Scope.ARTICLE, featured(uidOf(internalEvent))));
    }

    @Test
    void aPageNamesOnlyWhatIsPublic() {
        assertDoesNotThrow(() -> saved(Scope.PAGE, featured(uidOf(publicEvent))));
        refused(
                NewsRefusal.NEWS_BLOCK_ENTRY_NOT_PUBLIC,
                Scope.PAGE,
                teaser(internalNews.publicUid()),
                "internal entry");
        refused(
                EventRefusal.EVENT_BLOCK_APPOINTMENT_NOT_PUBLIC,
                Scope.PAGE,
                featured(uidOf(internalEvent)),
                "internal appointment");
    }

    @Test
    void noNewsBlockNamesWhatNotEveryReaderMayRead() {
        newsNobodyMayName().forEach((why, news) -> {
            refused(NewsRefusal.NEWS_BLOCK_ENTRY_NOT_FOR_EVERY_MEMBER, Scope.ARTICLE, teaser(news.publicUid()), why);
            refused(NewsRefusal.NEWS_BLOCK_ENTRY_NOT_PUBLIC, Scope.PAGE, teaser(news.publicUid()), why);
        });
        refused(NewsRefusal.NEWS_BLOCK_ENTRY_NOT_FOR_EVERY_MEMBER, Scope.ARTICLE, teaser(UUID.randomUUID()), "unknown");
    }

    @Test
    void noEventBlockNamesWhatNotEveryReaderMaySee() {
        eventsNobodyMayName().forEach((why, event) -> {
            refused(
                    EventRefusal.EVENT_BLOCK_APPOINTMENT_NOT_FOR_EVERY_MEMBER,
                    Scope.ARTICLE,
                    featured(uidOf(event)),
                    why);
            refused(EventRefusal.EVENT_BLOCK_APPOINTMENT_NOT_PUBLIC, Scope.PAGE, featured(uidOf(event)), why);
        });
    }

    @Test
    void aBlockOneLevelDownIsCheckedTheSame() {
        refused(
                EventRefusal.EVENT_BLOCK_APPOINTMENT_NOT_PUBLIC,
                Scope.PAGE,
                nested("FEATURED_EVENT", "{\"eventUid\":\"%s\"}".formatted(uidOf(internalEvent))),
                "nested internal appointment on a page");
        refused(
                NewsRefusal.NEWS_BLOCK_ENTRY_NOT_FOR_EVERY_MEMBER,
                Scope.ARTICLE,
                nested("NEWS_TEASER", "{\"newsUid\":\"%s\"}".formatted(groupNews.publicUid())),
                "nested restricted entry in an article");
        assertDoesNotThrow(() -> saved(
                Scope.ARTICLE, nested("NEWS_TEASER", "{\"newsUid\":\"%s\"}".formatted(internalNews.publicUid()))));
    }

    @Test
    void aBlockNamingNothingIsKept() {
        assertDoesNotThrow(
                () -> saved(Scope.PAGE, row(CellContentType.NEWS_TEASER, CellContentType.NEWS_TEASER.emptyConfig())));
        assertDoesNotThrow(() ->
                saved(Scope.PAGE, row(CellContentType.FEATURED_EVENT, CellContentType.FEATURED_EVENT.emptyConfig())));
    }

    @Test
    void contentOfTheInstanceNamesNothingAStationOwns() {
        var container = blocks.create(null);
        try {
            Executable save = () -> blocks.save(container.id(), List.of(teaser(publicNews.publicUid())), Scope.ARTICLE);
            assertEquals(
                    NewsRefusal.NEWS_BLOCK_ENTRY_NOT_FOR_EVERY_MEMBER,
                    assertThrows(RefusalResponse.class, save).refusal());
        } finally {
            blocks.delete(container.id());
        }
    }
}
