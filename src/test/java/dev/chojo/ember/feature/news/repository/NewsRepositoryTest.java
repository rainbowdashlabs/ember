/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.entity.BlockAudience;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int newsId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("News Station");
        account = accountRepo.create("news@test.com", "News", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var news = newsRepo.create(station.id(), "First News", "# Hello", "<h1>Hello</h1>", authorIdentity);
        assertNotNull(news);
        assertEquals("First News", news.title());
        newsId = news.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(newsRepo.findById(newsId).isPresent());
        assertTrue(newsRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findByStation() {
        var list = newsRepo.findByStation(station.id(), 0, 10);
        assertEquals(1, list.size());
    }

    @Test
    @Order(3)
    void anInstanceDraftStaysOutOfAStationsListUntilItIsPublished() {
        var draft = newsRepo.createSystem("Instance draft", "draft", "<p>draft</p>", false);
        var published = newsRepo.createSystem("Instance news", "news", "<p>news</p>", true);
        try {
            var ids = newsRepo.findByStation(station.id(), 0, 50).stream()
                    .map(News::id)
                    .toList();
            assertFalse(ids.contains(draft.id()));
            assertTrue(ids.contains(published.id()));
        } finally {
            newsRepo.delete(draft.id());
            newsRepo.delete(published.id());
        }
    }

    @Test
    @Order(4)
    void update() {
        assertTrue(newsRepo.update(newsId, "Updated News", "# Updated", "<h1>Updated</h1>"));
        assertEquals("Updated News", newsRepo.findById(newsId).orElseThrow().title());
    }

    @Test
    @Order(5)
    void findVisibleForMember() {
        var visible = newsRepo.findVisibleForMember(station.id(), member.id(), false, 0, 10);
        assertEquals(1, visible.size());
    }

    @Test
    @Order(10)
    void setAndFindRestrictions() {
        var group = memberGroupRepo.create(station.id(), "News Group");
        restrictionRepo.setRestrictions(
                RestrictionType.NEWS,
                newsId,
                new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), null));
        var restrictions = restrictionRepo.findRestrictions(RestrictionType.NEWS, newsId);
        assertEquals(1, restrictions.size());
        restrictionRepo.setRestrictions(RestrictionType.NEWS, newsId, RestrictionSelection.empty());
        assertTrue(
                restrictionRepo.findRestrictions(RestrictionType.NEWS, newsId).isEmpty());
        memberGroupRepo.delete(group.id());
    }

    @Test
    @Order(30)
    void acknowledge() {
        assertDoesNotThrow(() -> newsRepo.acknowledge(newsId, member.id()));
        assertDoesNotThrow(() -> newsRepo.acknowledge(newsId, member.id()));
    }

    @Test
    @Order(31)
    void isAcknowledged() {
        assertTrue(newsRepo.isAcknowledged(newsId, member.id()));
        assertFalse(newsRepo.isAcknowledged(newsId, 99999));
    }

    @Test
    @Order(32)
    void countUnacknowledged() {
        var account2 = accountRepo.create("news2@test.com", "News2", "User2");
        var member2 = stationMemberRepo.create(station.id(), account2.id());
        int unacked = newsRepo.countUnacknowledged(station.id(), member2.id(), false);
        assertEquals(1, unacked);
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(33)
    void countUnacknowledgedWhenAcknowledged() {
        int unacked = newsRepo.countUnacknowledged(station.id(), member.id(), false);
        assertEquals(0, unacked);
    }

    @Test
    @Order(40)
    void updatePublicBlog() {
        newsRepo.updatePublicBlog(newsId, true);
        assertTrue(newsRepo.findById(newsId).orElseThrow().publicBlog());
        newsRepo.updatePublicBlog(newsId, false);
        assertFalse(newsRepo.findById(newsId).orElseThrow().publicBlog());
    }

    @Test
    @Order(41)
    void findPublicBlogEntries() {
        newsRepo.updatePublicBlog(newsId, true);
        var entries = newsRepo.findPublicBlogEntries(station.id(), 0, 10);
        assertTrue(entries.stream().anyMatch(n -> n.id() == newsId));
        newsRepo.updatePublicBlog(newsId, false);
    }

    @Test
    @Order(41)
    void findPublicBlogEntriesWithSearch() {
        newsRepo.updatePublicBlog(newsId, true);
        var hits = newsRepo.findOpenEntries(station.id(), BlockAudience.PUBLIC, "updated", 0, 10);
        assertTrue(hits.stream().anyMatch(n -> n.id() == newsId));
        var misses = newsRepo.findOpenEntries(station.id(), BlockAudience.PUBLIC, "zzz-no-match", 0, 10);
        assertTrue(misses.stream().noneMatch(n -> n.id() == newsId));
        newsRepo.updatePublicBlog(newsId, false);
    }

    @Test
    @Order(41)
    void findPublicByUid() {
        newsRepo.updatePublicBlog(newsId, true);
        var uid = newsRepo.findById(newsId).orElseThrow().publicUid();
        assertTrue(
                newsRepo.findOpenByUid(station.id(), BlockAudience.PUBLIC, uid).isPresent());
        newsRepo.updatePublicBlog(newsId, false);
        assertTrue(
                newsRepo.findOpenByUid(station.id(), BlockAudience.PUBLIC, uid).isEmpty());
        assertTrue(
                newsRepo.findOpenByUid(station.id(), BlockAudience.MEMBERS, uid).isPresent());
    }

    @Test
    @Order(42)
    void hasPublicBlogEntries() {
        newsRepo.updatePublicBlog(newsId, true);
        assertTrue(newsRepo.hasPublicBlogEntries(station.id()));
        newsRepo.updatePublicBlog(newsId, false);
        assertFalse(newsRepo.hasPublicBlogEntries(station.id()));
    }

    @Test
    @Order(43)
    void saysWhichStationsHavePublicBlogEntries() {
        var quiet = stationRepo.create("News Station Quiet");
        newsRepo.updatePublicBlog(newsId, true);

        assertEquals(Set.of(station.id()), newsRepo.withPublicBlogEntries(List.of(station.id(), quiet.id())));
        newsRepo.updatePublicBlog(newsId, false);
        assertTrue(newsRepo.withPublicBlogEntries(List.of(station.id(), quiet.id()))
                .isEmpty());
        assertTrue(newsRepo.withPublicBlogEntries(List.of()).isEmpty());

        stationRepo.delete(quiet.id());
    }

    @Test
    @Order(50)
    void recordViewAndCount() {
        assertEquals(0, newsRepo.countViews(newsId));
        assertFalse(newsRepo.hasViewed(newsId, member.id()));
        newsRepo.recordView(newsId, member.id());
        assertEquals(1, newsRepo.countViews(newsId));
        assertTrue(newsRepo.hasViewed(newsId, member.id()));
        newsRepo.recordView(newsId, member.id());
        assertEquals(1, newsRepo.countViews(newsId));
    }

    @Test
    @Order(51)
    void findSeenAndUnseenViewers() {
        var account2 = accountRepo.create("news-views2@test.com", "News2", "Viewer");
        var member2 = stationMemberRepo.create(station.id(), account2.id());
        var seen = newsRepo.findSeenViewers(newsId);
        assertEquals(1, seen.size());
        assertEquals(member.uid(), seen.getFirst().member().memberUid());
        assertNotNull(seen.getFirst().seenAt());

        var unseen = newsRepo.findUnseenViewers(newsId, station.id(), List.of());
        assertTrue(unseen.stream().anyMatch(v -> v.member().memberUid().equals(member2.uid())));
        assertTrue(unseen.stream().allMatch(v -> v.seenAt() == null));

        accountRepo.delete(account2.id());
    }

    @Test
    @Order(99)
    void delete() {
        assertTrue(newsRepo.delete(newsId));
        assertTrue(newsRepo.findById(newsId).isEmpty());
    }

    /**
     * A member of one station reading another station's entry.
     *
     * <p>The listings never offered it, but a request that named the entry outright was answered in
     * full, author and all, for anyone logged in anywhere. The check the routes now make is this
     * one, so it is asserted where it is decided rather than only through the route.
     */
    @Test
    @Order(20)
    void anEntryIsNotVisibleToAMemberOfAnotherStation() {
        var otherStation = stationRepo.create("Other News Station");
        var otherAccount = accountRepo.create("other-news@test.com", "Other", "Reader");
        var otherMember = stationMemberRepo.create(otherStation.id(), otherAccount.id());
        try {
            assertTrue(newsRepo.isVisibleForMember(newsId, member.id(), false), "the station's own member reads it");
            assertFalse(
                    newsRepo.isVisibleForMember(newsId, otherMember.id(), false),
                    "a member of another station does not read it");
        } finally {
            stationRepo.delete(otherStation.id());
            accountRepo.delete(otherAccount.id());
        }
    }

    /**
     * An entry the instance published belongs to no station, and every station reads it.
     */
    @Test
    @Order(21)
    void aSystemEntryIsVisibleToEveryStation() {
        var systemNews =
                newsRepo.createSystem("Wartung", "Kurz nicht erreichbar.", "<p>Kurz nicht erreichbar.</p>", true);
        try {
            assertTrue(systemNews.systemEntry(), "it belongs to no station");
            assertTrue(newsRepo.isVisibleForMember(systemNews.id(), member.id(), false));
            assertTrue(
                    newsRepo.findVisibleForMember(station.id(), member.id(), false, 0, 50).stream()
                            .anyMatch(n -> n.id() == systemNews.id()),
                    "a station's news list holds it alongside its own");
        } finally {
            newsRepo.delete(systemNews.id());
        }
    }
}
