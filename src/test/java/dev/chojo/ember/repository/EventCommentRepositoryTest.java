/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventCommentRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int eventId;
    private static int commentId;
    private static int replyId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("CommentRepoStation");
        account = accountRepo.create("commentrepo@test.com", "Comment", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());

        var event = eventRepo.create(
                station.id(),
                "Comment Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventId = event.id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createTopLevelComment() {
        var author = memberIdentityFactory.local(station.id(), member.id());
        var comment = eventCommentRepo.create(eventId, null, author, "Hello world", null);
        assertNotNull(comment);
        assertEquals("Hello world", comment.content());
        assertNotNull(comment.author());
        assertEquals(author.memberUid(), comment.author().memberUid());
        assertNull(comment.parentId());
        assertFalse(comment.deleted());
        commentId = comment.id();
    }

    @Test
    @Order(2)
    void findById() {
        var found = eventCommentRepo.findById(commentId);
        assertTrue(found.isPresent());
        assertEquals("Hello world", found.get().content());
    }

    @Test
    @Order(3)
    void findByIdNotFound() {
        assertTrue(eventCommentRepo.findById(999999).isEmpty());
    }

    @Test
    @Order(4)
    void findByEvent() {
        var comments = eventCommentRepo.findByEvent(eventId);
        assertEquals(1, comments.size());
        assertEquals(commentId, comments.getFirst().id());
    }

    @Test
    @Order(5)
    void findByEventEmpty() {
        var comments = eventCommentRepo.findByEvent(999999);
        assertTrue(comments.isEmpty());
    }

    @Test
    @Order(6)
    void update() {
        assertTrue(eventCommentRepo.update(commentId, "Updated content"));
        var found = eventCommentRepo.findById(commentId).orElseThrow();
        assertEquals("Updated content", found.content());
        assertNotNull(found.updatedAt());
    }

    @Test
    @Order(7)
    void updateNotFound() {
        assertFalse(eventCommentRepo.update(999999, "new content"));
    }

    @Test
    @Order(8)
    void hasChildrenFalse() {
        assertFalse(eventCommentRepo.hasChildren(commentId));
    }

    @Test
    @Order(9)
    void createReply() {
        var author = memberIdentityFactory.local(station.id(), member.id());
        var reply = eventCommentRepo.create(eventId, commentId, author, "This is a reply", null);
        assertNotNull(reply);
        assertEquals(commentId, reply.parentId());
        replyId = reply.id();
    }

    @Test
    @Order(10)
    void hasChildrenTrue() {
        assertTrue(eventCommentRepo.hasChildren(commentId));
    }

    @Test
    @Order(11)
    void findByEventIncludesReply() {
        var comments = eventCommentRepo.findByEvent(eventId);
        assertEquals(2, comments.size());
    }

    @Test
    @Order(12)
    void deleteWithChildrenSoftDeletes() {
        // Deleting parent with children should soft-delete
        assertTrue(eventCommentRepo.delete(commentId));
        var found = eventCommentRepo.findById(commentId);
        assertTrue(found.isPresent());
        assertTrue(found.get().deleted());
        assertEquals("", found.get().content());
    }

    @Test
    @Order(13)
    void deleteWithoutChildrenHardDeletes() {
        // Deleting the reply (no children) should hard-delete
        assertTrue(eventCommentRepo.delete(replyId));
        assertTrue(eventCommentRepo.findById(replyId).isEmpty());
    }

    @Test
    @Order(14)
    void deleteSoftDeletedParentNowHardDeletes() {
        // Now parent has no children, deleting it should hard-delete
        assertTrue(eventCommentRepo.delete(commentId));
        assertTrue(eventCommentRepo.findById(commentId).isEmpty());
    }

    @Test
    @Order(15)
    void deleteNotFound() {
        assertFalse(eventCommentRepo.delete(999999));
    }

    @Test
    @Order(20)
    void createCommentWithNullAuthorForFederation() {
        // Comments with null author can be created for federated authors
        var comment = eventCommentRepo.create(eventId, null, null, "Federated-style comment", null);
        assertNotNull(comment);
        assertNull(comment.author());
        eventCommentRepo.delete(comment.id());
    }

    @Test
    @Order(30)
    void createCommentWithNullAuthor() {
        var comment = eventCommentRepo.create(eventId, null, null, "Anonymous comment", null);
        assertNotNull(comment);
        assertNull(comment.author());
        eventCommentRepo.delete(comment.id());
    }

    @Test
    @Order(31)
    void createMultipleTopLevelComments() {
        var author = memberIdentityFactory.local(station.id(), member.id());
        var c1 = eventCommentRepo.create(eventId, null, author, "Comment 1", null);
        var c2 = eventCommentRepo.create(eventId, null, author, "Comment 2", null);
        var c3 = eventCommentRepo.create(eventId, null, author, "Comment 3", null);

        var comments = eventCommentRepo.findByEvent(eventId);
        assertTrue(comments.size() >= 3);

        eventCommentRepo.delete(c3.id());
        eventCommentRepo.delete(c2.id());
        eventCommentRepo.delete(c1.id());
    }

    @Test
    @Order(32)
    void hasChildrenForNonExistent() {
        assertFalse(eventCommentRepo.hasChildren(999999));
    }

    @Test
    @Order(33)
    void deeplyNestedReplies() {
        var author = memberIdentityFactory.local(station.id(), member.id());
        var c1 = eventCommentRepo.create(eventId, null, author, "Level 0", null);
        var c2 = eventCommentRepo.create(eventId, c1.id(), author, "Level 1", null);
        var c3 = eventCommentRepo.create(eventId, c2.id(), author, "Level 2", null);

        assertTrue(eventCommentRepo.hasChildren(c1.id()));
        assertTrue(eventCommentRepo.hasChildren(c2.id()));
        assertFalse(eventCommentRepo.hasChildren(c3.id()));

        // Delete leaf first
        assertTrue(eventCommentRepo.delete(c3.id()));
        assertTrue(eventCommentRepo.findById(c3.id()).isEmpty());

        // c2 now has no children
        assertFalse(eventCommentRepo.hasChildren(c2.id()));
        assertTrue(eventCommentRepo.delete(c2.id()));
        assertTrue(eventCommentRepo.findById(c2.id()).isEmpty());

        // c1 now has no children
        assertTrue(eventCommentRepo.delete(c1.id()));
        assertTrue(eventCommentRepo.findById(c1.id()).isEmpty());
    }

    @Test
    @Order(40)
    void findByEventAndDateFiltersOccurrenceComments() {
        var author = memberIdentityFactory.local(station.id(), member.id());
        // Whole-event comment (event_date IS NULL)
        var whole = eventCommentRepo.create(eventId, null, author, "Whole event", null);
        // Two different occurrence dates
        var june =
                eventCommentRepo.create(eventId, null, author, "June occurrence", java.time.LocalDate.of(2026, 6, 1));
        var july =
                eventCommentRepo.create(eventId, null, author, "July occurrence", java.time.LocalDate.of(2026, 7, 1));

        var wholeOnly = eventCommentRepo.findByEventAndDate(eventId, null);
        assertEquals(1, wholeOnly.size());
        assertEquals("Whole event", wholeOnly.getFirst().content());
        assertNull(wholeOnly.getFirst().eventDate());

        var juneOnly = eventCommentRepo.findByEventAndDate(eventId, java.time.LocalDate.of(2026, 6, 1));
        assertEquals(1, juneOnly.size());
        assertEquals("June occurrence", juneOnly.getFirst().content());
        assertEquals(java.time.LocalDate.of(2026, 6, 1), juneOnly.getFirst().eventDate());

        // findByEvent still returns everything regardless of date.
        var all = eventCommentRepo.findByEvent(eventId);
        assertEquals(3, all.size());

        eventCommentRepo.delete(whole.id());
        eventCommentRepo.delete(june.id());
        eventCommentRepo.delete(july.id());
    }
}
