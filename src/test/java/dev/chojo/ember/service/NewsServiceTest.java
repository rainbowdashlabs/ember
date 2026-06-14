/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
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
class NewsServiceTest extends RepositoryTestBase {
    private static NewsService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int newsId;
    private static int commentId;

    @BeforeAll
    static void setup() {
        service = new NewsService(
                newsRepo,
                new RestrictionRepository(stationMemberRepo, memberGroupRepo, userTagRepo),
                new DomainEventBus(Set.of()),
                stationMemberRepo,
                accountRepo);
        station = stationRepo.create("NewsStation");
        account = accountRepo.create("news-svc@test.com", "News", "Author");
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
        var news = service.create(
                station.id(),
                "Test News",
                "Content of the news article",
                "<p>Content of the news article</p>",
                authorIdentity,
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertNotNull(news);
        assertEquals("Test News", news.title());
        newsId = news.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(newsId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        var list = service.findByStation(station.id(), 0, 100);
        assertTrue(list.stream().anyMatch(n -> n.id() == newsId));
    }

    @Test
    @Order(10)
    void update() {
        var result = service.update(
                newsId,
                "Updated News",
                "Updated content",
                "<p>Updated content</p>",
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertTrue(result.isPresent());
        assertEquals("Updated News", result.get().title());
    }

    @Test
    @Order(20)
    void createComment() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var comment =
                service.createComment(station.id(), newsId, null, authorIdentity, "News Author", "Great article!");
        assertNotNull(comment);
        commentId = comment.id();
    }

    @Test
    @Order(21)
    void findComments() {
        var comments = service.findComments(newsId);
        assertTrue(comments.stream().anyMatch(c -> c.id() == commentId));
    }

    @Test
    @Order(4)
    void findVisibleForMember() {
        var list = service.findVisibleForMember(station.id(), member.id(), 0, 100);
        assertTrue(list.stream().anyMatch(n -> n.id() == newsId));
    }

    @Test
    @Order(5)
    void countComments() {
        assertEquals(0, service.countComments(newsId));
    }

    @Test
    @Order(6)
    void findRestrictions() {
        var restrictions = service.findRestrictions(newsId);
        assertNotNull(restrictions);
    }

    @Test
    @Order(21)
    void findCommentById() {
        var comment = service.findCommentById(commentId);
        assertTrue(comment.isPresent());
        assertEquals("Great article!", comment.get().content());
    }

    @Test
    @Order(22)
    void updateComment() {
        assertTrue(service.updateComment(commentId, "Updated comment!"));
        var comment = service.findCommentById(commentId);
        assertTrue(comment.isPresent());
        assertEquals("Updated comment!", comment.get().content());
    }

    @Test
    @Order(23)
    void createReply() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var reply = service.createComment(
                station.id(), newsId, commentId, authorIdentity, "News Author", "This is a reply");
        assertNotNull(reply);
        assertEquals(commentId, reply.parentId());
    }

    @Test
    @Order(24)
    void countCommentsAfterCreation() {
        assertTrue(service.countComments(newsId) >= 2);
    }

    @Test
    @Order(25)
    void deleteNonExistentComment() {
        assertFalse(service.deleteComment(station.id(), -999));
    }

    @Test
    @Order(26)
    void deleteCommentWithChildrenSoftDeletes() {
        // Comment has a child reply, so it should be soft-deleted (not removed)
        assertTrue(service.deleteComment(station.id(), commentId));
        var deleted = service.findCommentById(commentId);
        assertTrue(deleted.isPresent());
        assertTrue(deleted.get().deleted());
        assertEquals("", deleted.get().content());
    }

    @Test
    @Order(27)
    void createCommentWithMention() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var mentionContent = "Hello @[member/" + member.uid() + ":News Author]!";
        var comment = service.createComment(station.id(), newsId, null, authorIdentity, "News Author", mentionContent);
        assertNotNull(comment);
        assertEquals(mentionContent, comment.content());
    }

    @Test
    @Order(27)
    void createCommentWithLegacyMention() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var mentionContent = "Hello @[999:Other Member]!";
        var comment = service.createComment(station.id(), newsId, null, authorIdentity, "News Author", mentionContent);
        assertNotNull(comment);
    }

    @Test
    @Order(27)
    void createCommentWithBulkMention() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var mentionContent = "Attention @[GROUP:TestGroup:1]!";
        var comment = service.createComment(station.id(), newsId, null, authorIdentity, "News Author", mentionContent);
        assertNotNull(comment);
    }

    @Test
    @Order(27)
    void createCommentWithLongContent() {
        var authorIdentity = stationMemberRepo.resolveIdentity(member.id());
        var longContent = "A".repeat(150);
        var comment = service.createComment(station.id(), newsId, null, authorIdentity, "News Author", longContent);
        assertNotNull(comment);
    }

    @Test
    @Order(27)
    void createCommentWithNullAuthor() {
        var comment = service.createComment(station.id(), newsId, null, null, "System", "System message");
        assertNotNull(comment);
    }

    @Test
    @Order(28)
    void recordAndListViewers() {
        // Idempotent: two calls only produce one row.
        service.recordView(newsId, member.id());
        service.recordView(newsId, member.id());

        var summary = service.findViewerSummary(newsId, station.id());
        assertEquals(1, summary.seen().size(), "the only member should appear in the seen list");
        assertNotNull(summary.seen().getFirst().seenAt());
        assertEquals(member.uid(), summary.seen().getFirst().member().memberUid());
        // The only eligible member has now seen the news, so unseen is empty.
        assertEquals(0, summary.unseen().size());
    }

    @Test
    @Order(29)
    void deleteNonExistentNews() {
        assertFalse(service.delete(-999));
    }

    @Test
    @Order(30)
    void delete() {
        assertTrue(service.delete(newsId));
        assertTrue(service.findById(newsId).isEmpty());
    }
}
