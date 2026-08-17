/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
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

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int newsId;
    private static int commentId;

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
    @Order(4)
    void update() {
        assertTrue(newsRepo.update(newsId, "Updated News", "# Updated", "<h1>Updated</h1>"));
        assertEquals("Updated News", newsRepo.findById(newsId).orElseThrow().title());
    }

    @Test
    @Order(5)
    void findVisibleForMember() {
        // No group restrictions, should be visible
        var visible = newsRepo.findVisibleForMember(station.id(), member.id(), 0, 10);
        assertEquals(1, visible.size());
    }

    // -- Restrictions (now handled by RestrictionRepository) --

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

    // -- Comments --

    @Test
    @Order(20)
    void createComment() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var comment = newsRepo.createComment(newsId, null, authorIdentity, "Great news!");
        assertNotNull(comment);
        assertEquals("Great news!", comment.content());
        commentId = comment.id();
    }

    @Test
    @Order(21)
    void findCommentsByNews() {
        assertEquals(1, newsRepo.findCommentsByNews(newsId).size());
    }

    @Test
    @Order(22)
    void countComments() {
        assertEquals(1, newsRepo.countComments(newsId));
    }

    @Test
    @Order(23)
    void findCommentById() {
        assertTrue(newsRepo.findCommentById(commentId).isPresent());
        assertTrue(newsRepo.findCommentById(99999).isEmpty());
    }

    @Test
    @Order(24)
    void updateComment() {
        assertTrue(newsRepo.updateComment(commentId, "Updated comment"));
        assertEquals(
                "Updated comment",
                newsRepo.findCommentById(commentId).orElseThrow().content());
    }

    @Test
    @Order(25)
    void createReply() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var reply = newsRepo.createComment(newsId, commentId, authorIdentity, "Reply to comment");
        assertNotNull(reply);
        assertEquals(commentId, reply.parentId());
        assertEquals(2, newsRepo.countComments(newsId));
        newsRepo.deleteComment(reply.id());
    }

    @Test
    @Order(26)
    void deleteComment() {
        assertTrue(newsRepo.deleteComment(commentId));
        assertEquals(0, newsRepo.countComments(newsId));
    }

    // -- Acknowledgements --

    @Test
    @Order(30)
    void acknowledge() {
        assertDoesNotThrow(() -> newsRepo.acknowledge(newsId, member.id()));
        // Idempotent - calling again should not throw
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
        // Create a second account/member that has not acknowledged
        var account2 = accountRepo.create("news2@test.com", "News2", "User2");
        var member2 = stationMemberRepo.create(station.id(), account2.id());
        int unacked = newsRepo.countUnacknowledged(station.id(), member2.id());
        // There is one published news article that member2 has not acknowledged
        assertEquals(1, unacked);
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(33)
    void countUnacknowledgedWhenAcknowledged() {
        // member already acknowledged the article in Order(30)
        int unacked = newsRepo.countUnacknowledged(station.id(), member.id());
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
        var hits = newsRepo.findPublicBlogEntries(station.id(), "updated", 0, 10);
        assertTrue(hits.stream().anyMatch(n -> n.id() == newsId));
        var misses = newsRepo.findPublicBlogEntries(station.id(), "zzz-no-match", 0, 10);
        assertTrue(misses.stream().noneMatch(n -> n.id() == newsId));
        newsRepo.updatePublicBlog(newsId, false);
    }

    @Test
    @Order(41)
    void findPublicByUid() {
        newsRepo.updatePublicBlog(newsId, true);
        var uid = newsRepo.findById(newsId).orElseThrow().publicUid();
        assertTrue(newsRepo.findPublicByUid(station.id(), uid).isPresent());
        newsRepo.updatePublicBlog(newsId, false);
        assertTrue(newsRepo.findPublicByUid(station.id(), uid).isEmpty());
    }

    @Test
    @Order(42)
    void hasPublicBlogEntries() {
        newsRepo.updatePublicBlog(newsId, true);
        assertTrue(newsRepo.hasPublicBlogEntries(station.id()));
        newsRepo.updatePublicBlog(newsId, false);
        assertFalse(newsRepo.hasPublicBlogEntries(station.id()));
    }

    // -- Views --

    @Test
    @Order(50)
    void recordViewAndCount() {
        // No views yet
        assertEquals(0, newsRepo.countViews(newsId));
        assertFalse(newsRepo.hasViewed(newsId, member.id()));
        newsRepo.recordView(newsId, member.id());
        assertEquals(1, newsRepo.countViews(newsId));
        assertTrue(newsRepo.hasViewed(newsId, member.id()));
        // Idempotent - second call adds no row
        newsRepo.recordView(newsId, member.id());
        assertEquals(1, newsRepo.countViews(newsId));
    }

    @Test
    @Order(51)
    void findSeenAndUnseenViewers() {
        var account2 = accountRepo.create("news-views2@test.com", "News2", "Viewer");
        var member2 = stationMemberRepo.create(station.id(), account2.id());
        // member has viewed, member2 has not
        var seen = newsRepo.findSeenViewers(newsId);
        assertEquals(1, seen.size());
        assertEquals(member.uid(), seen.getFirst().member().memberUid());
        assertNotNull(seen.getFirst().seenAt());

        var unseen = newsRepo.findUnseenViewers(newsId, station.id());
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
}
