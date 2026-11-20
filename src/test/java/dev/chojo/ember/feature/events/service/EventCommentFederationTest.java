/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.route.FederatedEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.feature.federation.FederationTestContracts.pathIs;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The comments on shared appointments, served to partners and written at partners. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventCommentFederationTest extends EventFederationTestBase {
    @Test
    @Order(50)
    void createRemoteComment() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        shareService.cacheName(partnerId, REMOTE_MEMBER_1, "Alice Remote");

        var response = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Hello from remote!", null);
        assertNotNull(response);
        assertEquals("Hello from remote!", response.content());
        assertFalse(response.deleted());
        assertNotNull(response.author());
        assertEquals(REMOTE_MEMBER_1, response.author().memberUid());
        assertEquals("Alice Remote", response.authorName());
    }

    @Test
    @Order(51)
    void createRemoteCommentWithParent() {
        var parent = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Parent comment", null);
        var reply = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_2, "Bob Remote", parent.id(), "Reply to parent", null);
        assertNotNull(reply);
        assertEquals(parent.id(), reply.parentId());
        assertEquals("Reply to parent", reply.content());
    }

    @Test
    @Order(52)
    void updateRemoteComment() {
        var created = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "Original content", null);
        var updated =
                commentFederation.updateRemoteComment(localPartner, created.id(), REMOTE_MEMBER_1, "Updated content");
        assertNotNull(updated);
        assertEquals("Updated content", updated.content());
        assertNotNull(updated.updatedAt());
    }

    @Test
    @Order(53)
    void updateRemoteCommentWrongOwner() {
        var created = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice Remote", null, "My comment", null);
        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.updateRemoteComment(localPartner, created.id(), REMOTE_MEMBER_2, "Hacked!"));
    }

    @Test
    @Order(54)
    void updateRemoteCommentNotFederated() {
        var localComment = localComment("Local comment");
        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.updateRemoteComment(
                        localPartner, localComment.id(), REMOTE_MEMBER_1, "Edited"));
    }

    @Test
    @Order(55)
    void deleteRemoteComment() {
        var created = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_3, "Delete Me", null, "To be deleted", null);
        boolean deleted = commentFederation.deleteRemoteComment(localPartner, created.id(), REMOTE_MEMBER_3);
        assertTrue(deleted);
    }

    @Test
    @Order(56)
    void deleteRemoteCommentWrongOwner() {
        var created = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_1, "Alice", null, "My comment", null);
        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.deleteRemoteComment(localPartner, created.id(), REMOTE_MEMBER_2));
    }

    @Test
    @Order(57)
    void deleteRemoteCommentNotFederated() {
        var localComment = localComment("Local comment to delete");
        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.deleteRemoteComment(localPartner, localComment.id(), REMOTE_MEMBER_1));
    }

    /**
     * A partner's reply to a member here, mentioning that member, tells nobody here; its removal
     * still withdraws what was written about it.
     */
    @Test
    @Order(58)
    void aPartnersCommentTellsNobodyButItsRemovalIsAnnounced() {
        var local = localComment("Frage von hier");
        reset(COMMENT_BUS);

        var reply = commentFederation.createRemoteComment(
                localPartner,
                eventId,
                REMOTE_MEMBER_3,
                "Partner",
                local.id(),
                "@[%s/%s:Hier] Antwort".formatted(testMemberIdentity.stationUid(), testMemberIdentity.memberUid()),
                null);
        verify(COMMENT_BUS, never()).publish(any());

        commentFederation.deleteRemoteComment(localPartner, reply.id(), REMOTE_MEMBER_3);
        verify(COMMENT_BUS)
                .publish(argThat(event -> event instanceof CommentDeleted deleted
                        && deleted.commentId() == reply.id()
                        && deleted.entityType() == CommentEntityType.EVENT));
    }

    @Test
    @Order(60)
    void toCommentResponseDeletedComment() {
        var comment = localComment("Will be deleted");
        commentService.delete(comment);
        var deletedComment = new Comment(
                comment.id(),
                comment.type(),
                comment.targetId(),
                comment.stationId(),
                comment.eventDate(),
                comment.parentId(),
                comment.author(),
                "",
                true,
                comment.createdAt(),
                comment.updatedAt());
        var response = commentFederation.toCommentResponse(deletedComment);
        assertTrue(response.deleted());
        assertEquals("", response.content());
        assertNull(response.authorName());
        assertNull(response.author());
    }

    @Test
    @Order(61)
    void toCommentResponseLocalAuthor() {
        var comment = localComment("Local author comment");
        var response = commentFederation.toCommentResponse(comment);
        assertFalse(response.deleted());
        assertEquals("Local author comment", response.content());
        assertNotNull(response.author());
        assertNotNull(response.author().stationUid());
        assertNotNull(response.author().memberUid());
        assertTrue(response.authorName().contains("Test"));
        assertTrue(response.authorName().contains("Author"));
    }

    @Test
    @Order(62)
    void toCommentResponseFederatedAuthor() {
        var created = commentFederation.createRemoteComment(
                localPartner, eventId, REMOTE_MEMBER_2, "Bob Federated", null, "Federated comment", null);
        var comment =
                commentService.findById(CommentEntityType.EVENT, created.id()).orElseThrow();
        var response = commentFederation.toCommentResponse(comment);
        assertFalse(response.deleted());
        assertEquals("Federated comment", response.content());
        assertNotNull(response.author());
        assertEquals(REMOTE_MEMBER_2, response.author().memberUid());
        assertNotNull(response.author().stationUid());
        assertEquals("Bob Federated", response.authorName());
    }

    @Test
    @Order(63)
    void listComments() {
        var comments = commentFederation.listComments(eventId);
        assertNotNull(comments);
        assertFalse(comments.isEmpty(), "Should have comments from earlier tests");
        assertTrue(comments.stream().allMatch(c -> c.id() > 0));
    }

    @Test
    @Order(70)
    void listFederatedCommentsLocal() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var result = commentFederation.listFederatedComments(stationB.id(), stationA.uid(), eventId);
        assertNotNull(result);
        transport.assertParity(
                partnerOf(stationB, stationA),
                RemoteEventRoutes.LIST_COMMENTS.at(eventId),
                null,
                CommentResponse.class);
    }

    @Test
    @Order(71)
    void listFederatedCommentsRemote() {
        var mockResponses = List.of(new CommentResponse(
                1,
                null,
                null,
                null,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote User",
                "Remote comment",
                false,
                Instant.now(),
                null,
                null));
        when(httpClient.getList(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId + "/comments"),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(mockResponses);

        var result = commentFederation.listFederatedComments(stationA.id(), stationC.uid(), eventId);
        assertEquals(1, result.size());
        assertEquals("Remote comment", result.getFirst().content());
    }

    @Test
    @Order(72)
    void listFederatedCommentsUnknownPartner() {
        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.listFederatedComments(stationA.id(), UUID.randomUUID(), eventId));
    }

    @Test
    @Order(73)
    void createFederatedCommentLocal() {
        var result = commentFederation.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Local federated create", null);
        assertEquals("Local federated create", result.content());
    }

    /**
     * A partner on this instance used to comment on any appointment of the station it is paired
     * with, shared or not, while a partner on another instance was refused. Both are refused now.
     */
    @Test
    @Order(73)
    void commentsOnAnAppointmentNotSharedWithAPartnerHereAreRefused() {
        var unshared = eventRepo.create(
                stationA.id(),
                "Unshared Event",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS),
                null,
                true,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        var refused = assertThrows(
                RefusalResponse.class,
                () -> commentFederation.createFederatedComment(
                        stationB.id(), stationA.uid(), unshared.id(), REMOTE_MEMBER_1, "Alice", null, "No", null));
        assertEquals(EventRefusal.EVENT_NOT_SHARED_WITH_PARTNER, refused.refusal());
        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.listFederatedComments(stationB.id(), stationA.uid(), unshared.id()));
        assertThrows(
                RefusalResponse.class,
                () -> clientService.getFederatedEvent(stationB.id(), stationA.uid(), unshared.id()));
    }

    @Test
    @Order(74)
    void createFederatedCommentRemote() {
        var mockResponse = new CommentResponse(
                99,
                null,
                null,
                null,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote Author",
                "Remote created",
                false,
                Instant.now(),
                null,
                null);
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId + "/comments"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(mockResponse);

        var result = commentFederation.createFederatedComment(
                stationA.id(), stationC.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Remote content", null);
        assertEquals("Remote created", result.content());
    }

    @Test
    @Order(75)
    void createFederatedCommentRemoteReturnsNull() {
        when(httpClient.post(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/" + eventId + "/comments"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(null);

        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.createFederatedComment(
                        stationA.id(), stationC.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Will fail", null));
    }

    @Test
    @Order(76)
    void updateFederatedCommentLocal() {
        var createResult = commentFederation.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "To update locally", null);
        int commentId = createResult.id();

        var result = commentFederation.updateFederatedComment(
                stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_1, "Updated locally");
        assertEquals("Updated locally", result.content());
    }

    @Test
    @Order(77)
    void updateFederatedCommentLocalWrongOwner() {
        var createResult = commentFederation.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Owner check", null);
        int commentId = createResult.id();

        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.updateFederatedComment(
                        stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_2, "Wrong owner"));
    }

    @Test
    @Order(78)
    void updateFederatedCommentRemote() {
        var mockResponse = new CommentResponse(
                100,
                null,
                null,
                null,
                null,
                new MemberIdentity(stationC.uid(), REMOTE_MEMBER_1),
                "Remote",
                "Updated remote",
                false,
                Instant.now(),
                Instant.now(),
                null);
        when(httpClient.put(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/100"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(mockResponse);

        var result = commentFederation.updateFederatedComment(
                stationA.id(), stationC.uid(), 100, REMOTE_MEMBER_1, "Updated remote");
        assertEquals("Updated remote", result.content());
    }

    @Test
    @Order(79)
    void updateFederatedCommentRemoteReturnsNull() {
        when(httpClient.put(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/200"),
                        any(),
                        any(),
                        eq(stationA.id()),
                        eq(CommentResponse.class)))
                .thenReturn(null);

        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.updateFederatedComment(
                        stationA.id(), stationC.uid(), 200, REMOTE_MEMBER_1, "Will fail"));
    }

    @Test
    @Order(80)
    void deleteFederatedCommentLocal() {
        var createResult = commentFederation.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_3, "Charlie", null, "Delete me locally", null);
        int commentId = createResult.id();

        commentFederation.deleteFederatedComment(stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_3);
        assertTrue(commentService
                .findById(CommentEntityType.EVENT, commentId)
                .map(Comment::deleted)
                .orElse(true));
    }

    @Test
    @Order(81)
    void deleteFederatedCommentLocalWrongOwner() {
        var createResult = commentFederation.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, REMOTE_MEMBER_1, "Alice", null, "Delete check", null);
        int commentId = createResult.id();

        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.deleteFederatedComment(
                        stationB.id(), stationA.uid(), commentId, REMOTE_MEMBER_2));
    }

    @Test
    @Order(82)
    void deleteFederatedCommentRemote() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/300"),
                        eq(new RemoteEventRoutes.RemoteCommentDeleteRequest(REMOTE_MEMBER_1)),
                        any(),
                        eq(stationA.id())))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> commentFederation.deleteFederatedComment(stationA.id(), stationC.uid(), 300, REMOTE_MEMBER_1));
    }

    @Test
    @Order(83)
    void deleteFederatedCommentRemoteFails() {
        when(httpClient.delete(
                        eq("https://remote-event.example.com"),
                        pathIs("/remote/events/comments/301"),
                        any(),
                        any(),
                        eq(stationA.id())))
                .thenReturn(false);

        assertThrows(
                RefusalResponse.class,
                () -> commentFederation.deleteFederatedComment(stationA.id(), stationC.uid(), 301, REMOTE_MEMBER_1));
    }

    /** What a partner here sends that cannot be taken is refused in the words of what went wrong. */
    @Test
    @Order(95)
    void aPartnerHereIsToldWhatWasWrongWithItsComment() {
        shareService.setShare(eventId, ShareScope.ALL_PARTNERS, List.of());
        var asking = partnerOf(stationB, stationA);
        var member = UUID.randomUUID();

        assertRefused(
                EventRefusal.PARTNER_COMMENT_NEEDS_TEXT,
                () -> commentFederation.createFederatedComment(
                        stationB.id(), stationA.uid(), eventId, member, "Visitor", null, " ", null));
        assertRefused(EventRefusal.PARTNER_COMMENT_DAY_NOT_A_DATE, () -> transport
                .transport()
                .send(
                        asking,
                        RemoteEventRoutes.CREATE_COMMENT.at(eventId),
                        new RemoteEventRoutes.RemoteCommentRequest(member, "Visitor", null, "Hi", "someday"),
                        CommentResponse.class));
        var comment = commentFederation.createFederatedComment(
                stationB.id(), stationA.uid(), eventId, member, "Visitor", null, "Hi", null);
        assertRefused(
                EventRefusal.PARTNER_COMMENT_CHANGE_NEEDS_TEXT,
                () -> commentFederation.updateFederatedComment(
                        stationB.id(), stationA.uid(), comment.id(), member, " "));
        assertEquals(
                "ok",
                transport
                        .transport()
                        .send(
                                asking,
                                RemoteEventRoutes.REGISTRATION_STATUS_WEBHOOK.at(),
                                null,
                                FederatedEventRoutes.StatusResponse.class)
                        .status());
    }
}
