/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContentBlockServiceTest extends RepositoryTestBase {

    private static ContentBlockService blocks;
    private static Station station;
    private static Account account;

    @BeforeAll
    static void setup() {
        blocks = new ContentBlockService(contentContainerRepo);
        station = stationRepo.create("ContentBlockStation");
        account = accountRepo.create("content-block@test.com", "Content", "Block");
        stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static ContentBlockService.RowData row(CellContentType type, String content, CellConfig config) {
        return new ContentBlockService.RowData(
                0, List.of(new ContentBlockService.CellData(0, 100.0, type, content, config)));
    }

    @Test
    void savingReplacesEverythingInTheContainer() {
        var container = blocks.create(station.id());
        try {
            blocks.save(
                    container.id(),
                    List.of(row(CellContentType.MARKDOWN, "erst", CellConfig.EMPTY)),
                    ContentBlockService.Scope.PAGE);
            assertEquals(1, blocks.loadRows(container.id()).size());

            blocks.save(
                    container.id(),
                    List.of(
                            row(CellContentType.MARKDOWN, "dann", CellConfig.EMPTY),
                            row(CellContentType.DIVIDER, "", new CellConfig.DividerConfig(null))),
                    ContentBlockService.Scope.PAGE);
            var rows = blocks.loadRows(container.id());
            assertEquals(2, rows.size());
            assertEquals("dann", rows.getFirst().cells().getFirst().content());
        } finally {
            blocks.delete(container.id());
        }
    }

    @Test
    void aPageOnlyBlockIsRefusedInAnArticleWhateverTheChooserOffered() {
        var container = blocks.create(station.id());
        try {
            var withheld = List.of(row(CellContentType.BLOG_SIGNUP, "", new CellConfig.BlogSignupConfig("A", "B")));
            assertThrows(
                    BadRequestResponse.class,
                    () -> blocks.save(container.id(), withheld, ContentBlockService.Scope.ARTICLE));
            assertDoesNotThrow(() -> blocks.save(container.id(), withheld, ContentBlockService.Scope.PAGE));
        } finally {
            blocks.delete(container.id());
        }
    }

    @Test
    void theAllowlistRecursesIntoNestedRows() {
        var container = blocks.create(station.id());
        try {
            var nested = CellConfig.parse(
                    CellContentType.NESTED_ROWS,
                    CellConfig.MAPPER.readTree("{\"rows\":[{\"cells\":[{\"contentType\":\"MEMBER_SPOTLIGHT\"}]}]}"));
            var rows = List.of(row(CellContentType.NESTED_ROWS, "", nested));
            assertThrows(
                    BadRequestResponse.class,
                    () -> blocks.save(container.id(), rows, ContentBlockService.Scope.ARTICLE),
                    "a withheld block one level down is still withheld");

            var harmless = CellConfig.parse(
                    CellContentType.NESTED_ROWS,
                    CellConfig.MAPPER.readTree(
                            "{\"rows\":[{\"cells\":[{\"contentType\":\"MARKDOWN\"},{\"contentType\":\"NOT_A_BLOCK\"},{}]},{}]}"));
            assertDoesNotThrow(() -> blocks.save(
                    container.id(),
                    List.of(row(CellContentType.NESTED_ROWS, "", harmless)),
                    ContentBlockService.Scope.ARTICLE));
        } finally {
            blocks.delete(container.id());
        }
    }

    @Test
    void ensureGivesBackTheContainerThatIsAlreadyThere() {
        var container = blocks.create(station.id());
        try {
            assertEquals(
                    container.id(), blocks.ensure(station.id(), container.id()).id());
            assertNotEquals(container.id(), blocks.ensure(station.id(), null).id());
            assertNotEquals(
                    container.id(),
                    blocks.ensure(station.id(), 99999).id(),
                    "a container that has gone gets replaced rather than crashing the save");
            assertTrue(blocks.find(container.id()).isPresent());
            assertTrue(blocks.find(99999).isEmpty());
        } finally {
            blocks.delete(container.id());
        }
    }

    @Test
    void copyingCarriesEveryBlockAcross() {
        var source = blocks.create(station.id());
        var target = blocks.create(station.id());
        try {
            blocks.save(
                    source.id(),
                    List.of(
                            row(CellContentType.MARKDOWN, "text", CellConfig.EMPTY),
                            row(CellContentType.DIVIDER, "", new CellConfig.DividerConfig(null))),
                    ContentBlockService.Scope.PAGE);
            blocks.copyInto(source.id(), target.id());

            var copied = blocks.loadRows(target.id());
            assertEquals(2, copied.size());
            assertEquals("text", copied.getFirst().cells().getFirst().content());
        } finally {
            blocks.delete(source.id());
            blocks.delete(target.id());
        }
    }

    @Test
    void deletingNothingIsNotAnError() {
        assertDoesNotThrow(() -> blocks.delete(null));
        assertDoesNotThrow(() -> blocks.delete(99999));
    }
}
