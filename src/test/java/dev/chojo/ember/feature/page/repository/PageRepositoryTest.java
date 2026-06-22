/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PageRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int pageId;
    private static int childPageId;
    private static int imageId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Page Station");
        account = accountRepo.create("page@test.com", "Page", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createPage() {
        var page = pageRepo.create(station.id(), "Welcome", "welcome", null, member.id());
        assertNotNull(page);
        assertEquals("Welcome", page.title());
        assertEquals("welcome", page.slug());
        assertNull(page.parentId());
        assertFalse(page.published());
        pageId = page.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(pageRepo.findById(pageId).isPresent());
        assertTrue(pageRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        var list = pageRepo.findByStation(station.id());
        assertEquals(1, list.size());
        assertEquals("Welcome", list.getFirst().title());
    }

    @Test
    @Order(4)
    void slugExists() {
        assertTrue(pageRepo.slugExists(station.id(), "welcome", 0));
        assertFalse(pageRepo.slugExists(station.id(), "welcome", pageId));
        assertFalse(pageRepo.slugExists(station.id(), "nonexistent", 0));
    }

    @Test
    @Order(5)
    void updateMeta() {
        assertTrue(pageRepo.updateMeta(pageId, "Updated", "updated-slug", null, "A description", null));
        var page = pageRepo.findById(pageId).orElseThrow();
        assertEquals("Updated", page.title());
        assertEquals("updated-slug", page.slug());
        assertEquals("A description", page.metaDescription());
    }

    @Test
    @Order(6)
    void setPublished() {
        assertTrue(pageRepo.setPublished(pageId, true));
        assertTrue(pageRepo.findById(pageId).orElseThrow().published());
    }

    @Test
    @Order(7)
    void findPublishedByStation() {
        var list = pageRepo.findPublishedByStation(station.id());
        assertEquals(1, list.size());
    }

    @Test
    @Order(8)
    void createChildPage() {
        var child = pageRepo.create(station.id(), "Child Page", "child-page", pageId, member.id());
        assertNotNull(child);
        assertEquals(pageId, child.parentId());
        childPageId = child.id();
    }

    @Test
    @Order(9)
    void depth() {
        assertEquals(0, pageRepo.depth(pageId));
        assertEquals(1, pageRepo.depth(childPageId));
    }

    @Test
    @Order(10)
    void insertRowsAndCells() {
        int rowId = pageRepo.insertRow(pageId, 0);
        assertTrue(rowId > 0);
        pageRepo.insertCell(rowId, 0, 60.0, CellContentType.MARKDOWN, "<h1>Hello</h1>", CellConfig.EMPTY);
        pageRepo.insertCell(
                rowId,
                1,
                40.0,
                CellContentType.IMAGE,
                "1",
                new CellConfig.ImageConfig(
                        CellConfig.ImageFit.COVER, null, null, null, null, null, null, null, null, null, null));

        var rows = pageRepo.findRowsByPage(pageId);
        assertEquals(1, rows.size());

        var cells = pageRepo.findCellsByRow(rowId);
        assertEquals(2, cells.size());
        assertEquals(CellContentType.MARKDOWN, cells.getFirst().contentType());
        assertEquals(60.0, cells.getFirst().widthPercent());
    }

    @Test
    @Order(11)
    void loadFullTree() {
        var page = pageRepo.findById(pageId).orElseThrow();
        var full = pageRepo.loadFullTree(page);
        assertEquals(1, full.rows().size());
        assertEquals(2, full.rows().getFirst().cells().size());
    }

    @Test
    @Order(12)
    void deleteRowsByPage() {
        pageRepo.deleteRowsByPage(pageId);
        assertEquals(0, pageRepo.findRowsByPage(pageId).size());
    }

    @Test
    @Order(13)
    void createFile() {
        var image = pageRepo.createFile(pageId, station.id(), "abc123", "test.png", "image/png", 1024);
        assertNotNull(image);
        assertEquals("test.png", image.fileName());
        assertEquals(1024, image.fileSize());
        imageId = image.id();
    }

    @Test
    @Order(14)
    void findFile() {
        assertTrue(pageRepo.findFile(imageId).isPresent());
        assertTrue(pageRepo.findFile(99999).isEmpty());
    }

    @Test
    @Order(15)
    void findFilesByPage() {
        var list = pageRepo.findFilesByPage(pageId);
        assertEquals(1, list.size());
    }

    @Test
    @Order(16)
    void findAllCellsByPage() {
        int rowId = pageRepo.insertRow(pageId, 0);
        pageRepo.insertCell(rowId, 0, 50.0, CellContentType.IMAGE, String.valueOf(imageId), CellConfig.EMPTY);
        pageRepo.insertCell(rowId, 1, 50.0, CellContentType.MARKDOWN, "text", CellConfig.EMPTY);

        var cells = pageRepo.findAllCellsByPage(pageId);
        assertEquals(2, cells.size());
        assertTrue(cells.stream()
                .anyMatch(c -> c.contentType() == CellContentType.IMAGE
                        && String.valueOf(imageId).equals(c.content())));

        pageRepo.deleteRowsByPage(pageId);
    }

    @Test
    @Order(17)
    void deleteFile() {
        assertTrue(pageRepo.deleteFile(imageId));
        assertTrue(pageRepo.findFile(imageId).isEmpty());
    }

    @Test
    @Order(18)
    void landingPage() {
        pageRepo.setLandingPage(station.id(), pageId);
        assertEquals(pageId, pageRepo.getLandingPageId(station.id()).orElseThrow());

        pageRepo.setLandingPage(station.id(), null);
        assertTrue(pageRepo.getLandingPageId(station.id()).isEmpty());
    }

    @Test
    @Order(19)
    void findBySlugAndStation() {
        var page = pageRepo.findBySlugAndStation("updated-slug", station.id());
        assertTrue(page.isPresent());
        assertEquals(pageId, page.orElseThrow().id());

        assertTrue(pageRepo.findBySlugAndStation("nonexistent", station.id()).isEmpty());
    }

    @Test
    @Order(20)
    void countChildren() {
        assertEquals(1, pageRepo.countChildren(pageId));
        assertEquals(0, pageRepo.countChildren(childPageId));
    }

    @Test
    @Order(21)
    void findBySlugAndParent() {
        var rootBySlug = pageRepo.findBySlugAndParent(station.id(), "updated-slug", null);
        assertTrue(rootBySlug.isPresent());
        assertEquals(pageId, rootBySlug.orElseThrow().id());

        var rootMissing = pageRepo.findBySlugAndParent(station.id(), "missing", null);
        assertTrue(rootMissing.isEmpty());

        var childBySlug = pageRepo.findBySlugAndParent(station.id(), "child-page", pageId);
        assertTrue(childBySlug.isPresent());
        assertEquals(childPageId, childBySlug.orElseThrow().id());

        var childMissing = pageRepo.findBySlugAndParent(station.id(), "child-page", 99999);
        assertTrue(childMissing.isEmpty());
    }

    @Test
    @Order(22)
    void searchForPicker() {
        var pickerPage = pageRepo.create(station.id(), "Picker Match", "picker-match", null, member.id());
        try {
            pageRepo.setPublished(pickerPage.id(), true);
            var unmatched = pageRepo.create(station.id(), "Unmatched", "unmatched", null, member.id());
            pageRepo.setPublished(unmatched.id(), true);

            var all = pageRepo.searchForPicker(station.id(), null, 50);
            assertTrue(all.stream().anyMatch(p -> "picker-match".equals(p.slug())));

            var matches = pageRepo.searchForPicker(station.id(), "picker", 50);
            assertTrue(matches.stream().anyMatch(p -> "picker-match".equals(p.slug())));
            assertTrue(matches.stream().noneMatch(p -> "unmatched".equals(p.slug())));

            var none = pageRepo.searchForPicker(station.id(), "no-such-page-anywhere", 50);
            assertTrue(none.isEmpty());

            var first = matches.getFirst();
            assertNotNull(first.pageUid());
            assertNotNull(first.title());
            assertNotNull(first.slug());
            assertNotNull(first.updatedAt());

            pageRepo.delete(unmatched.id());
        } finally {
            pageRepo.delete(pickerPage.id());
        }
    }

    @Test
    @Order(23)
    void findByStationAndHash() {
        var img = pageRepo.createFile(pageId, station.id(), "hashAAA", "h.png", "image/png", 16);
        try {
            var found = pageRepo.findByStationAndHash(station.id(), "hashAAA");
            assertTrue(found.isPresent());
            assertEquals(img.id(), found.orElseThrow().id());
            assertTrue(
                    pageRepo.findByStationAndHash(station.id(), "hashMissing").isEmpty());
        } finally {
            pageRepo.deleteFile(img.id());
        }
    }

    @Test
    @Order(24)
    void updateFileMeta() {
        var img = pageRepo.createFile(pageId, station.id(), "metaHash", "meta.png", "image/png", 8);
        try {
            assertTrue(pageRepo.updateFileMeta(img.id(), "alt text", "description text"));
            var fetched = pageRepo.findFile(img.id()).orElseThrow();
            assertEquals("alt text", fetched.defaultAltText());
            assertEquals("description text", fetched.defaultDescription());
            assertFalse(pageRepo.updateFileMeta(99999, "x", "y"));
        } finally {
            pageRepo.deleteFile(img.id());
        }
    }

    @Test
    @Order(25)
    void findFilesByStationAndAllCellsByStation() {
        var img = pageRepo.createFile(pageId, station.id(), "stHash", "st.png", "image/png", 8);
        int rowId = pageRepo.insertRow(pageId, 0);
        pageRepo.insertCell(rowId, 0, 100.0, CellContentType.MARKDOWN, "txt", CellConfig.EMPTY);
        try {
            var files = pageRepo.findFilesByStation(station.id());
            assertTrue(files.stream().anyMatch(f -> f.id() == img.id()));

            var allCells = pageRepo.findAllCellsByStation(station.id());
            assertFalse(allCells.isEmpty());
        } finally {
            pageRepo.deleteRowsByPage(pageId);
            pageRepo.deleteFile(img.id());
        }
    }

    @Test
    @Order(100)
    void deleteChild() {
        assertTrue(pageRepo.delete(childPageId));
        assertTrue(pageRepo.findById(childPageId).isEmpty());
    }

    @Test
    @Order(101)
    void deletePage() {
        assertTrue(pageRepo.delete(pageId));
        assertTrue(pageRepo.findById(pageId).isEmpty());
    }
}
