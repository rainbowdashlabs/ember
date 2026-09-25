/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.PageVisibility;
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
        assertEquals(PageVisibility.DRAFT, page.visibility());
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
    void setVisibility() {
        assertTrue(pageRepo.setVisibility(pageId, PageVisibility.PUBLIC, null));
        assertEquals(
                PageVisibility.PUBLIC, pageRepo.findById(pageId).orElseThrow().visibility());
    }

    @Test
    @Order(6)
    void aPageBecomingUnlistedIsMintedALinkInTheSameBreath() {
        var page = pageRepo.create(station.id(), "Einladung", "einladung", null, member.id());
        assertTrue(pageRepo.setVisibility(page.id(), PageVisibility.UNLISTED, "erster-token"));
        assertEquals("erster-token", pageRepo.findShareToken(page.id()).orElseThrow());

        pageRepo.setVisibility(page.id(), PageVisibility.PUBLIC, "zweiter-token");
        pageRepo.setVisibility(page.id(), PageVisibility.UNLISTED, "dritter-token");
        assertEquals(
                "erster-token",
                pageRepo.findShareToken(page.id()).orElseThrow(),
                "a link already sent keeps working when a page is opened and closed again");

        assertTrue(pageRepo.replaceShareToken(page.id(), "erster-token", "vierter-token"));
        assertFalse(
                pageRepo.replaceShareToken(page.id(), "erster-token", "fuenfter-token"),
                "whoever was shown the old link is told it changed rather than ending somebody else's");
        pageRepo.delete(page.id());
    }

    @Test
    @Order(7)
    void findListedByStation() {
        var list = pageRepo.findListedByStation(station.id());
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
            pageRepo.setVisibility(pickerPage.id(), PageVisibility.PUBLIC, null);
            var unmatched = pageRepo.create(station.id(), "Unmatched", "unmatched", null, member.id());
            pageRepo.setVisibility(unmatched.id(), PageVisibility.PUBLIC, null);

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
