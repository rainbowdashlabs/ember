/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.station.entity.Station;
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
    void createImage() {
        var image = pageRepo.createImage(pageId, "test.png", "image/png", 1024);
        assertNotNull(image);
        assertEquals("test.png", image.fileName());
        assertEquals(1024, image.fileSize());
        imageId = image.id();
    }

    @Test
    @Order(14)
    void findImage() {
        assertTrue(pageRepo.findImage(imageId).isPresent());
        assertTrue(pageRepo.findImage(99999).isEmpty());
    }

    @Test
    @Order(15)
    void findImagesByPage() {
        var list = pageRepo.findImagesByPage(pageId);
        assertEquals(1, list.size());
    }

    @Test
    @Order(16)
    void findReferencedImageIds() {
        // Add a row with an IMAGE cell referencing the uploaded image
        int rowId = pageRepo.insertRow(pageId, 0);
        pageRepo.insertCell(rowId, 0, 50.0, CellContentType.IMAGE, String.valueOf(imageId), CellConfig.EMPTY);
        pageRepo.insertCell(rowId, 1, 50.0, CellContentType.MARKDOWN, "text", CellConfig.EMPTY);

        var referenced = pageRepo.findReferencedImageIds(pageId);
        assertEquals(1, referenced.size());
        assertTrue(referenced.contains(imageId));

        // Clean up row
        pageRepo.deleteRowsByPage(pageId);
    }

    @Test
    @Order(17)
    void deleteImage() {
        assertTrue(pageRepo.deleteImage(imageId));
        assertTrue(pageRepo.findImage(imageId).isEmpty());
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
