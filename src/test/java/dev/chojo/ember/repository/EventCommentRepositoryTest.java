/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventCommentRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int eventId;
    private static int commentId;
    private static int replyId;
    private static FederationRepository federationRepo;

    @BeforeAll
    static void setup() {
        federationRepo = new FederationRepository();
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
        var comment = eventCommentRepo.create(eventId, null, member.id(), "Hello world");
        assertNotNull(comment);
        assertEquals("Hello world", comment.content());
        assertEquals(member.id(), comment.authorId());
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
        var reply = eventCommentRepo.create(eventId, commentId, member.id(), "This is a reply");
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
    void setAndFindFederatedAuthor() {
        // Create a comment for federation test
        var comment = eventCommentRepo.create(eventId, null, member.id(), "Federated comment");
        assertNotNull(comment);

        // Create a federation partner
        var partner = federationRepo.createPartner(
                station.id(), UUID.randomUUID(), "invite-code-test", "publickey123", "https://remote.example.com");
        assertNotNull(partner);

        UUID remoteMemberId = UUID.randomUUID();
        eventCommentRepo.setFederatedAuthor(comment.id(), partner.id(), remoteMemberId);

        var fedAuthor = eventCommentRepo.findFederatedAuthor(comment.id());
        assertTrue(fedAuthor.isPresent());
        assertEquals(comment.id(), fedAuthor.get().commentId());
        assertEquals(partner.id(), fedAuthor.get().partnerId());
        assertEquals(remoteMemberId, fedAuthor.get().remoteMemberId());

        // Clean up
        eventCommentRepo.delete(comment.id());
    }

    @Test
    @Order(21)
    void findFederatedAuthorNotFound() {
        assertTrue(eventCommentRepo.findFederatedAuthor(999999).isEmpty());
    }

    @Test
    @Order(30)
    void createCommentWithNullAuthor() {
        var comment = eventCommentRepo.create(eventId, null, null, "Anonymous comment");
        assertNotNull(comment);
        assertNull(comment.authorId());
        eventCommentRepo.delete(comment.id());
    }

    @Test
    @Order(31)
    void createMultipleTopLevelComments() {
        var c1 = eventCommentRepo.create(eventId, null, member.id(), "Comment 1");
        var c2 = eventCommentRepo.create(eventId, null, member.id(), "Comment 2");
        var c3 = eventCommentRepo.create(eventId, null, member.id(), "Comment 3");

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
        var c1 = eventCommentRepo.create(eventId, null, member.id(), "Level 0");
        var c2 = eventCommentRepo.create(eventId, c1.id(), member.id(), "Level 1");
        var c3 = eventCommentRepo.create(eventId, c2.id(), member.id(), "Level 2");

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
}
