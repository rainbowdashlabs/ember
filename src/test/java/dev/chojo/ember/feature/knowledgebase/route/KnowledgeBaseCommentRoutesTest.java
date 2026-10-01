/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.KnowledgeBaseRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.repository.CommentRepository;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbCommentTarget;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A comment on a knowledge-base file is changed only by its author and removed by its author or a
 * knowledge manager, and only while the file is the station's.
 */
class KnowledgeBaseCommentRoutesTest {
    private static final int STATION = 3;
    private static final int FILE = 20;
    private static final MemberIdentity AUTHOR = new MemberIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000011"));
    private static final MemberIdentity SOMEBODY_ELSE = new MemberIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000012"));

    private CommentRepository comments;
    private RouteHarness harness;

    private static Comment comment(int id, MemberIdentity author, String content) {
        return new Comment(
                id, CommentEntityType.KB, FILE, STATION, null, null, author, content, false, Instant.EPOCH, null);
    }

    @BeforeEach
    void setup() {
        comments = mock(CommentRepository.class);
        var files = mock(KnowledgeBaseRepository.class);
        var file = mock(KbFile.class);
        when(file.id()).thenReturn(FILE);
        when(file.stationId()).thenReturn(STATION);
        when(file.name()).thenReturn("Handbuch");
        when(files.findFileById(FILE)).thenReturn(Optional.of(file));
        var identities = mock(MemberIdentityFactory.class);
        when(identities.local(STATION, TestSessions.MEMBER_ID)).thenReturn(AUTHOR);
        var names = mock(MemberNameResolver.class);
        when(names.resolveDisplay(any()))
                .thenAnswer(call -> new MemberNameResolver.ResolvedMember(call.getArgument(0), "Anna"));
        when(comments.findById(CommentEntityType.KB, 5)).thenReturn(Optional.of(comment(5, AUTHOR, "alt")));
        when(comments.findById(CommentEntityType.KB, 6)).thenReturn(Optional.of(comment(6, SOMEBODY_ELSE, "fremd")));
        var service = new CommentService(
                comments,
                Map.of(CommentEntityType.KB, new KbCommentTarget(files)),
                mock(DomainEventBus.class),
                mock(StationMemberService.class),
                mock(StationRepository.class),
                mock(CommentMentions.class));
        harness = RouteHarness.serving(
                new KnowledgeBaseCommentRoutes(service, mock(KbAuthorNameService.class), identities, names));
    }

    @Test
    void theAuthorChangesTheirCommentAndReadsItBackAsStored() {
        var author = harness.as(TestSessions.member(STATION));
        when(comments.update(CommentEntityType.KB, 5, "neu")).thenReturn(true);
        when(comments.findById(CommentEntityType.KB, 5))
                .thenReturn(Optional.of(comment(5, AUTHOR, "alt")))
                .thenReturn(Optional.of(comment(5, AUTHOR, "neu")));

        var answer = harness.request(
                client -> client.put(PREFIX + "/kb/comments/5", body("{\"content\": \"neu\"}"), author));

        assertEquals("neu", json(answer).path("content").asString());
        verify(comments).update(CommentEntityType.KB, 5, "neu");
    }

    @Test
    void somebodyElsesCommentIsNeitherChangedNorRemovedWithoutTheRight() {
        var member = harness.as(TestSessions.member(STATION));

        harness.run((server, client) -> {
            assertEquals(
                    KnowledgeBaseRefusal.KB_COMMENT_NOT_YOURS_TO_CHANGE,
                    refusalOf(client.put(PREFIX + "/kb/comments/6", body("{\"content\": \"x\"}"), member)));
            assertEquals(
                    KnowledgeBaseRefusal.KB_COMMENT_NOT_YOURS_TO_DELETE,
                    refusalOf(client.delete(PREFIX + "/kb/comments/6", null, member)));
        });

        verify(comments, never()).delete(any(), anyInt());
    }

    @Test
    void aKnowledgeManagerRemovesAnyCommentOfTheStation() {
        when(comments.delete(CommentEntityType.KB, 6)).thenReturn(true);
        var manager = harness.as(TestSessions.member(STATION, StationPermission.KNOWLEDGE_MANAGER));

        var answer = harness.request(client -> client.delete(PREFIX + "/kb/comments/6", null, manager));

        assertEquals(204, answer.code());
    }

    @Test
    void anUnknownCommentIsNotFound() {
        var answer = harness.request(
                client -> client.delete(PREFIX + "/kb/comments/9", null, harness.as(TestSessions.member(STATION))));

        assertEquals(KnowledgeBaseRefusal.KB_COMMENT_NOT_HERE, refusalOf(answer));
    }

    @Test
    void anotherStationsCommentIsNotHere() {
        var answer = harness.request(
                client -> client.delete(PREFIX + "/kb/comments/5", null, harness.as(TestSessions.member(STATION + 1))));

        assertEquals(GeneralRefusal.NOT_HERE_OR_NOT_YOURS, refusalOf(answer));
    }

    @Test
    void aCommentGoneAfterTheChangeIsReportedSo() {
        var author = harness.as(TestSessions.member(STATION));

        var answer = harness.request(
                client -> client.put(PREFIX + "/kb/comments/5", body("{\"content\": \"neu\"}"), author));

        assertEquals(KnowledgeBaseRefusal.KB_COMMENT_NOT_HERE_AFTER_CHANGE, refusalOf(answer));
    }
}
