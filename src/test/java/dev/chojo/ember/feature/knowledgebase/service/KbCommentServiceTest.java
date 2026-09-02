/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.BulkMentionedInComment;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KbCommentServiceTest extends RepositoryTestBase {
    private static KbCommentService service;
    private static KbCommentRepository commentRepository;
    private static DomainEventBus eventBus;
    private static StationMemberService stationMemberService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int fileId;

    @BeforeAll
    static void setup() {
        commentRepository = mock(KbCommentRepository.class);
        eventBus = mock(DomainEventBus.class);
        stationMemberService = mock(StationMemberService.class);
        service = new KbCommentService(
                knowledgeBaseRepo, commentRepository, memberIdentityFactory, stationMemberService, eventBus);
        station = stationRepo.create("KbCommentStation");
        account = accountRepo.create("kb-comment@test.com", "Kb", "CommentTester");
        member = stationMemberRepo.create(station.id(), account.id());
        fileId = knowledgeBaseRepo
                .createFile(
                        station.id(),
                        null,
                        "Commented File",
                        "",
                        KbFileType.MARKDOWN,
                        "text/markdown",
                        0,
                        null,
                        member.id())
                .id();
    }

    @AfterAll
    static void cleanup() {
        knowledgeBaseRepo.purgeFile(fileId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static KbComment storedComment(int id, Integer parentId, String content) {
        return new KbComment(id, fileId, parentId, null, content, false, Instant.now(), null);
    }

    private static List<DomainEvent> publishedEvents() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
    }

    @BeforeEach
    void resetMocks() {
        reset(eventBus, commentRepository, stationMemberService);
    }

    /**
     * A comment announces itself once and then fans out one notification per mention: the
     * current {@code station/member} form, the legacy numeric form, and the bulk group form all
     * reach their audience from the same body of text.
     */
    @Test
    void commentMentionsNotifyEveryMentionedAudience() {
        UUID mentionedUid = UUID.randomUUID();
        int mentionedMemberId = member.id() + 1000;
        int numericMemberId = member.id() + 2000;
        String content = "Ping @[%s/%s:Alice] and @[%d:Bob] and @[GROUP:Crew:7]"
                .formatted(station.uid(), mentionedUid, numericMemberId);
        when(commentRepository.create(anyInt(), any(), any(), anyString()))
                .thenReturn(storedComment(500, null, content));
        when(stationMemberService.resolveId(station.id(), mentionedUid)).thenReturn(Optional.of(mentionedMemberId));

        var comment = service.createComment(station.id(), fileId, null, member.id(), "Author", content);
        assertEquals(500, comment.id());

        var events = publishedEvents();
        var created = events.stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(1, created.size());
        assertEquals(500, created.getFirst().commentId());
        assertEquals(fileId, created.getFirst().entityId());
        assertEquals("Commented File", created.getFirst().entityTitle());
        assertNull(created.getFirst().parentAuthorId());

        var mentioned = events.stream()
                .filter(MentionedInComment.class::isInstance)
                .map(MentionedInComment.class::cast)
                .map(MentionedInComment::mentionedMemberId)
                .toList();
        assertTrue(mentioned.contains(mentionedMemberId), "the uuid mention must be delivered");
        assertFalse(
                mentioned.contains(numericMemberId),
                "a bare numeric mention names a member on the whole instance and must reach nobody");

        var bulk = events.stream()
                .filter(BulkMentionedInComment.class::isInstance)
                .map(BulkMentionedInComment.class::cast)
                .toList();
        assertEquals(1, bulk.size());
        assertEquals(MentionType.GROUP, bulk.getFirst().mentionType());
        assertEquals(7, bulk.getFirst().mentionTargetId());
    }

    /**
     * Authors are never notified about their own mentions, and a mention whose identifier is not
     * a usable member reference is dropped rather than failing the comment.
     */
    @Test
    void selfMentionsAndUnusableMentionsAreDropped() {
        UUID selfUid = UUID.randomUUID();
        UUID unknownUid = UUID.randomUUID();
        String content = "@[%s/%s:Me] @[%s/not-a-uuid:Broken] @[%s/%s:Ghost] @[%d:Self]"
                .formatted(station.uid(), selfUid, station.uid(), station.uid(), unknownUid, member.id());
        when(commentRepository.create(anyInt(), any(), any(), anyString()))
                .thenReturn(storedComment(501, null, content));
        when(stationMemberService.resolveId(station.id(), selfUid)).thenReturn(Optional.of(member.id()));
        when(stationMemberService.resolveId(station.id(), unknownUid)).thenReturn(Optional.empty());

        service.createComment(station.id(), fileId, null, member.id(), "Author", content);

        assertTrue(
                publishedEvents().stream().noneMatch(MentionedInComment.class::isInstance),
                "no mention notification should survive");
    }

    /**
     * Replies carry the parent author so the notification pipeline can tell "someone replied to
     * you" apart from "someone commented".
     */
    @Test
    void repliesCarryTheParentAuthor() {
        var parentIdentity = new MemberIdentity(station.uid(), UUID.randomUUID());
        when(commentRepository.create(anyInt(), any(), any(), anyString()))
                .thenReturn(storedComment(502, 400, "reply body"));
        when(commentRepository.findById(400))
                .thenReturn(Optional.of(
                        new KbComment(400, fileId, null, parentIdentity, "parent body", false, Instant.now(), null)));
        when(stationMemberService.resolveMemberId(parentIdentity)).thenReturn(Optional.of(77));

        service.createComment(station.id(), fileId, 400, member.id(), "Author", "reply body");

        var created = publishedEvents().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(400, created.parentCommentId());
        assertEquals(77, created.parentAuthorId());
    }

    /**
     * A reply to a comment nobody can be resolved for still goes out, just without a parent author
     * to notify.
     */
    @Test
    void repliesToAnUnresolvableParentStillAnnounceThemselves() {
        when(commentRepository.create(anyInt(), any(), any(), anyString()))
                .thenReturn(storedComment(504, 401, "orphan reply"));
        when(commentRepository.findById(401)).thenReturn(Optional.empty());

        service.createComment(station.id(), fileId, 401, member.id(), "Author", "orphan reply");

        var created = publishedEvents().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(401, created.parentCommentId());
        assertNull(created.parentAuthorId());
    }

    /**
     * A comment on a file that no longer exists still announces itself, just without a title.
     */
    @Test
    void commentsOnAMissingFileAnnounceThemselvesWithoutATitle() {
        when(commentRepository.create(anyInt(), any(), any(), anyString()))
                .thenReturn(storedComment(505, null, "ghost file"));

        service.createComment(station.id(), 999999, null, member.id(), "Author", "ghost file");

        var created = publishedEvents().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("", created.entityTitle());
    }

    /**
     * Long comments are truncated before they travel into a notification, so the preview stays
     * short enough to render inline.
     */
    @Test
    void longCommentsAreTruncatedIntoAShortPreview() {
        String content = "x".repeat(150);
        when(commentRepository.create(anyInt(), any(), any(), anyString()))
                .thenReturn(storedComment(503, null, content));

        service.createComment(station.id(), fileId, null, member.id(), "Author", content);

        var created = publishedEvents().stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("x".repeat(100) + "...", created.preview());
    }

    /**
     * Removing a comment says nothing to anybody: what was written about it points at the file it
     * sits under, which is still there.
     */
    @Test
    void deletingACommentAnnouncesNothing() {
        when(commentRepository.findById(600)).thenReturn(Optional.of(storedComment(600, null, "farewell")));
        when(commentRepository.delete(600)).thenReturn(true);

        assertTrue(service.deleteComment(station.id(), 600));

        verify(eventBus, never()).publish(any());
    }

    /**
     * Nothing happens for a comment that does not exist, or for one the repository refuses to
     * remove.
     */
    @Test
    void deletingAMissingCommentAnnouncesNothing() {
        when(commentRepository.findById(601)).thenReturn(Optional.empty());
        when(commentRepository.findById(602)).thenReturn(Optional.of(storedComment(602, null, "stubborn")));
        when(commentRepository.delete(602)).thenReturn(false);

        assertFalse(service.deleteComment(station.id(), 601));
        assertFalse(service.deleteComment(station.id(), 602));

        verify(eventBus, never()).publish(any());
    }
}
