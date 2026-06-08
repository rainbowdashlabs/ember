/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CommentServiceTest extends RepositoryTestBase {
    private static CommentService service;
    private static Station station;
    private static Account account1;
    private static Account account2;
    private static StationMember member1;
    private static StationMember member2;
    private static MemberIdentity identity1;
    private static MemberIdentity identity2;
    private static int eventId;
    private static int commentId;
    private static int replyId;
    private static DomainEventBus eventBus;

    @BeforeAll
    static void setup() {
        eventBus = mock(DomainEventBus.class);
        service = new CommentService(
                eventCommentRepo,
                eventBus,
                new StationMemberService(stationMemberRepo, stationRepo, null, null),
                stationRepo);

        station = stationRepo.create("CommentStation");
        account1 = accountRepo.create("comment1@test.com", "Alice", "Author");
        account2 = accountRepo.create("comment2@test.com", "Bob", "Mentioned");
        member1 = stationMemberRepo.create(station.id(), account1.id());
        member2 = stationMemberRepo.create(station.id(), account2.id());
        identity1 = memberIdentityFactory.local(station.id(), member1.id());
        identity2 = memberIdentityFactory.local(station.id(), member2.id());

        // Create a test event
        var event = eventRepo.create(
                station.id(),
                "Test Event",
                null,
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
        accountRepo.delete(account1.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(1)
    void createComment() {
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", "Hello world", "Test Event");
        assertNotNull(comment);
        assertEquals("Hello world", comment.content());
        assertNotNull(comment.author());
        assertEquals(identity1.memberUid(), comment.author().memberUid());
        assertNull(comment.parentId());
        commentId = comment.id();
    }

    @Test
    @Order(2)
    void findByEvent() {
        var comments = service.findByEvent(eventId);
        assertEquals(1, comments.size());
        assertEquals(commentId, comments.getFirst().id());
    }

    @Test
    @Order(3)
    void findById() {
        var comment = service.findById(commentId);
        assertTrue(comment.isPresent());
        assertEquals("Hello world", comment.get().content());
    }

    @Test
    @Order(4)
    void createReplyNotifiesParentAuthor() {
        reset(eventBus);
        var reply = service.create(station.id(), eventId, commentId, identity2, "Bob", "Nice comment!", "Test Event");
        assertNotNull(reply);
        assertEquals(commentId, reply.parentId());
        replyId = reply.id();

        // Verify a CommentCreated event was published to notify the parent comment author (member1)
        verify(eventBus).publish(argThat(event -> {
            if (!(event instanceof CommentCreated c)) return false;
            return c.stationId() == station.id()
                    && c.entityType() == CommentEntityType.EVENT
                    && c.entityId() == eventId
                    && c.parentAuthorId() == member1.id()
                    && c.authorMemberId() == member2.id()
                    && "Bob".equals(c.authorName());
        }));
    }

    @Test
    @Order(5)
    void createReplyToOwnCommentDoesNotNotify() {
        reset(eventBus);
        // Reply to own comment — should NOT publish CommentCreated
        var selfReply = service.create(
                station.id(), eventId, commentId, identity1, "Alice", "Replying to myself", "Test Event");
        assertNotNull(selfReply);
        verify(eventBus, never()).publish(argThat(event -> event instanceof CommentCreated));
        service.delete(selfReply.id());
    }

    @Test
    @Order(6)
    void findByEventIncludesReply() {
        var comments = service.findByEvent(eventId);
        assertEquals(2, comments.size());
    }

    @Test
    @Order(6)
    void createWithMentionPublishesEvent() {
        reset(eventBus);
        String content = "Hey @[" + member2.id() + ":Bob] check this out!";
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", content, "Test Event");
        assertNotNull(comment);
        assertEquals(content, comment.content());

        // Verify that a MentionedInComment event was published for member2
        verify(eventBus).publish(argThat(event -> {
            if (!(event
                    instanceof
                    MentionedInComment(
                            int stationId,
                            int mentionedMemberId,
                            Integer authorMemberId,
                            String authorName,
                            CommentEntityType entityType,
                            int entityId,
                            String entityTitle))) return false;
            return stationId == station.id()
                    && mentionedMemberId == member2.id()
                    && authorMemberId == member1.id()
                    && "Alice".equals(authorName)
                    && entityType == CommentEntityType.EVENT
                    && entityId == eventId;
        }));
    }

    @Test
    @Order(7)
    void createWithSelfMentionDoesNotPublish() {
        reset(eventBus);
        String content = "Talking about @[" + member1.id() + ":Alice] myself";
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", content, "Test Event");
        assertNotNull(comment);
        assertEquals(content, comment.content());

        // Self-mention should NOT trigger a notification
        verify(eventBus, never()).publish(any());
    }

    @Test
    @Order(8)
    void update() {
        assertTrue(service.update(commentId, "Updated content"));
        var comment = service.findById(commentId);
        assertTrue(comment.isPresent());
        assertEquals("Updated content", comment.get().content());
    }

    @Test
    @Order(9)
    void deleteReply() {
        assertTrue(service.delete(replyId));
        assertTrue(service.findById(replyId).isEmpty());
    }

    @Test
    @Order(10)
    void deleteNonExistent() {
        assertFalse(service.delete(-999));
    }

    @Test
    @Order(11)
    void deleteOriginal() {
        assertTrue(service.delete(commentId));
        assertTrue(service.findById(commentId).isEmpty());
    }

    @Test
    @Order(12)
    void findByIdNonExistent() {
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(13)
    void updateNonExistent() {
        assertFalse(service.update(999999, "new content"));
    }

    @Test
    @Order(14)
    void createWithMultipleMentions() {
        reset(eventBus);

        // Multiple mentions in one comment — both different from author
        String content = "Hey @[" + member2.id() + ":Bob] and @[" + member2.id() + ":Bob] again";
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", content, "Test Event");
        assertNotNull(comment);
        // eventBus should have been called for member2 twice
        verify(eventBus, times(2))
                .publish(argThat(
                        event -> event instanceof MentionedInComment m && m.mentionedMemberId() == member2.id()));

        service.delete(comment.id());
    }

    @Test
    @Order(15)
    void createWithNoMentions() {
        reset(eventBus);
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", "No mentions here", "Test Event");
        assertNotNull(comment);
        // No mentions — eventBus should not be called
        verify(eventBus, never()).publish(any());
        service.delete(comment.id());
    }

    @Test
    @Order(16)
    void updateExistingComment() {
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", "Original", "Test Event");
        assertTrue(service.update(comment.id(), "Modified"));
        var found = service.findById(comment.id()).orElseThrow();
        assertEquals("Modified", found.content());
        service.delete(comment.id());
    }

    @Test
    @Order(17)
    void deleteCommentWithChildrenSoftDeletes() {
        // Create parent and child comments
        var parent = service.create(station.id(), eventId, null, identity1, "Alice", "Parent comment", "Test Event");
        var child = service.create(station.id(), eventId, parent.id(), identity2, "Bob", "Child comment", "Test Event");

        // Delete parent — should soft-delete since it has children
        assertTrue(service.delete(parent.id()));
        var deleted = service.findById(parent.id());
        assertTrue(deleted.isPresent());
        assertTrue(deleted.get().deleted());
        assertEquals("", deleted.get().content());

        // Child should still exist
        assertTrue(service.findById(child.id()).isPresent());

        // Clean up
        service.delete(child.id());
        service.delete(parent.id());
    }

    @Test
    @Order(18)
    void deleteCommentWithoutChildrenHardDeletes() {
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", "Standalone", "Test Event");
        assertTrue(service.delete(comment.id()));
        assertTrue(service.findById(comment.id()).isEmpty());
    }

    @Test
    @Order(19)
    void createWithNewFormatMention() {
        reset(eventBus);
        // New format: @[stationUid/memberUid:Name]
        String content = "Hey @[" + station.uid() + "/" + member2.uid() + ":Bob] look!";
        var comment = service.create(station.id(), eventId, null, identity1, "Alice", content, "Test Event");
        assertNotNull(comment);
        verify(eventBus)
                .publish(argThat(
                        event -> event instanceof MentionedInComment m && m.mentionedMemberId() == member2.id()));
        service.delete(comment.id());
    }

    @Test
    @Order(20)
    void createWithNullAuthorSkipsNotifications() {
        reset(eventBus);
        // Federated comments with null author skip mention parsing and reply notifications
        var comment = service.create(
                station.id(), eventId, null, null, null, "Federated comment @[" + member2.id() + ":Bob]", "Test Event");
        assertNotNull(comment);
        assertNull(comment.author());
        verify(eventBus, never()).publish(any());
        service.delete(comment.id());
    }

    @Test
    @Order(21)
    void findByEventReturnsEmpty() {
        // Create a separate event with no comments
        var event2 = eventRepo.create(
                station.id(),
                "Empty Event",
                null,
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
        var comments = service.findByEvent(event2.id());
        assertTrue(comments.isEmpty());
        eventRepo.delete(event2.id());
    }
}
