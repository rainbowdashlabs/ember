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
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.comment.repository.CommentRepository;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
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
    private static CommentRepository commentRepository;
    private static DomainEventBus eventBus;
    private static StationMemberService stationMemberService;
    private static MemberLookupService memberLookup;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int fileId;

    @BeforeAll
    static void setup() {
        commentRepository = mock(CommentRepository.class);
        eventBus = mock(DomainEventBus.class);
        stationMemberService = mock(StationMemberService.class);
        memberLookup = mock(MemberLookupService.class);
        service = new KbCommentService(
                knowledgeBaseRepo,
                commentRepository,
                memberIdentityFactory,
                stationMemberService,
                eventBus,
                new CommentMentions(memberLookup, eventBus));
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

    private static Comment storedComment(int id, Integer parentId, String content) {
        return stored(id, parentId, null, content);
    }

    private static Comment stored(int id, Integer parentId, MemberIdentity author, String content) {
        return new Comment(
                id,
                CommentEntityType.KB,
                fileId,
                station.id(),
                null,
                parentId,
                author,
                content,
                false,
                Instant.now(),
                null);
    }

    private static List<DomainEvent> publishedEvents() {
        var captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventBus, atLeastOnce()).publish(captor.capture());
        return captor.getAllValues();
    }

    @BeforeEach
    void resetMocks() {
        reset(eventBus, commentRepository, stationMemberService, memberLookup);
    }

    @Test
    void aCommentIsFoundByItsId() {
        when(commentRepository.findById(CommentEntityType.KB, 500))
                .thenReturn(Optional.of(storedComment(500, null, "Hallo")));

        assertEquals("Hallo", service.findComment(500).orElseThrow().content());
        assertTrue(service.findComment(501).isEmpty());
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
        when(commentRepository.create(eq(CommentEntityType.KB), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(storedComment(500, null, content));
        when(memberLookup.resolveId(station.id(), mentionedUid)).thenReturn(Optional.of(mentionedMemberId));

        var comment = service.createComment(station.id(), fileId, null, member.id(), "Author", content);
        assertEquals(500, comment.id());

        var events = publishedEvents();
        var created = events.stream()
                .filter(CommentCreated.class::isInstance)
                .map(CommentCreated.class::cast)
                .toList();
        assertEquals(1, created.size());
        assertEquals(500, created.getFirst().commentId());
        assertEquals(
                NotificationLinks.comment(NotificationLinks.kbFile(fileId), 500),
                created.getFirst().link());
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
        when(commentRepository.create(eq(CommentEntityType.KB), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(storedComment(501, null, content));
        when(memberLookup.resolveId(station.id(), selfUid)).thenReturn(Optional.of(member.id()));
        when(memberLookup.resolveId(station.id(), unknownUid)).thenReturn(Optional.empty());

        service.createComment(station.id(), fileId, null, member.id(), "Author", content);

        assertTrue(
                publishedEvents().stream().noneMatch(MentionedInComment.class::isInstance),
                "no mention notification should survive");
    }

    /**
     * An edit that adds a mention tells the member it adds, and only them: whoever the comment
     * already mentioned, member or audience, heard about it when it was written.
     */
    @Test
    void editsAnnounceOnlyTheMentionsTheyAdd() {
        UUID keptUid = UUID.randomUUID();
        UUID addedUid = UUID.randomUUID();
        int keptMemberId = member.id() + 3000;
        int addedMemberId = member.id() + 4000;
        String before = "Hi @[%s/%s:Kept] @[GROUP:Crew:7]".formatted(station.uid(), keptUid);
        String after = before + " and @[%s/%s:Added] @[EVENT:Everyone:9]".formatted(station.uid(), addedUid);
        when(commentRepository.findById(CommentEntityType.KB, 502))
                .thenReturn(Optional.of(storedComment(502, null, before)));
        when(memberLookup.resolveId(station.id(), keptUid)).thenReturn(Optional.of(keptMemberId));
        when(memberLookup.resolveId(station.id(), addedUid)).thenReturn(Optional.of(addedMemberId));

        service.updateComment(station.id(), 502, member.id(), "Author", after);

        verify(commentRepository).update(CommentEntityType.KB, 502, after);
        var events = publishedEvents();
        var mentioned = events.stream()
                .filter(MentionedInComment.class::isInstance)
                .map(MentionedInComment.class::cast)
                .toList();
        assertEquals(1, mentioned.size());
        assertEquals(addedMemberId, mentioned.getFirst().mentionedMemberId());
        assertEquals(502, mentioned.getFirst().commentId());
        assertEquals("Commented File", mentioned.getFirst().entityTitle());
        var bulk = events.stream()
                .filter(BulkMentionedInComment.class::isInstance)
                .map(BulkMentionedInComment.class::cast)
                .toList();
        assertEquals(1, bulk.size());
        assertEquals(MentionType.EVENT, bulk.getFirst().mentionType());
    }

    /** An edit that leaves the mentions as they were tells nobody anything. */
    @Test
    void editsThatKeepTheMentionsAnnounceNothing() {
        UUID keptUid = UUID.randomUUID();
        String before = "Hi @[%s/%s:Kept]".formatted(station.uid(), keptUid);
        when(commentRepository.findById(CommentEntityType.KB, 503))
                .thenReturn(Optional.of(storedComment(503, null, before)));
        when(memberLookup.resolveId(station.id(), keptUid)).thenReturn(Optional.of(member.id() + 3000));

        service.updateComment(station.id(), 503, member.id(), "Author", before + " (typo fixed)");

        verify(eventBus, never()).publish(any());
    }

    /**
     * Replies carry the parent author so the notification pipeline can tell "someone replied to
     * you" apart from "someone commented".
     */
    @Test
    void repliesCarryTheParentAuthor() {
        var parentIdentity = new MemberIdentity(station.uid(), UUID.randomUUID());
        when(commentRepository.create(eq(CommentEntityType.KB), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(storedComment(502, 400, "reply body"));
        when(commentRepository.findById(CommentEntityType.KB, 400))
                .thenReturn(Optional.of(stored(400, null, parentIdentity, "parent body")));
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
        when(commentRepository.create(eq(CommentEntityType.KB), anyInt(), any(), any(), any(), anyString()))
                .thenReturn(storedComment(504, 401, "orphan reply"));
        when(commentRepository.findById(CommentEntityType.KB, 401)).thenReturn(Optional.empty());

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
        when(commentRepository.create(eq(CommentEntityType.KB), anyInt(), any(), any(), any(), anyString()))
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
        when(commentRepository.create(eq(CommentEntityType.KB), anyInt(), any(), any(), any(), anyString()))
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
     * Removing a comment announces which comment went, so that what was written about that one can
     * be withdrawn while the file it sat under keeps its own notifications.
     */
    @Test
    void deletingACommentAnnouncesWhichCommentWent() {
        when(commentRepository.findById(CommentEntityType.KB, 600))
                .thenReturn(Optional.of(storedComment(600, null, "farewell")));
        when(commentRepository.delete(CommentEntityType.KB, 600)).thenReturn(true);

        assertTrue(service.deleteComment(station.id(), 600));

        var deleted = publishedEvents().stream()
                .filter(CommentDeleted.class::isInstance)
                .map(CommentDeleted.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(600, deleted.commentId());
        assertEquals(CommentEntityType.KB, deleted.entityType());
        assertEquals(station.id(), deleted.stationId());
    }

    /**
     * Nothing happens for a comment that does not exist, or for one the repository refuses to
     * remove.
     */
    @Test
    void deletingAMissingCommentAnnouncesNothing() {
        when(commentRepository.findById(CommentEntityType.KB, 601)).thenReturn(Optional.empty());
        when(commentRepository.findById(CommentEntityType.KB, 602))
                .thenReturn(Optional.of(storedComment(602, null, "stubborn")));
        when(commentRepository.delete(CommentEntityType.KB, 602)).thenReturn(false);

        assertFalse(service.deleteComment(station.id(), 601));
        assertFalse(service.deleteComment(station.id(), 602));

        verify(eventBus, never()).publish(any());
    }
}
