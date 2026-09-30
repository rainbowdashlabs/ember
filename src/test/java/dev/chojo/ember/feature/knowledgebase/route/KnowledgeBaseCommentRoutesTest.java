/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbCommentService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    private static final MemberIdentity AUTHOR = new MemberIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000011"));
    private static final MemberIdentity SOMEBODY_ELSE = new MemberIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000012"));

    private KbCommentService comments;
    private RouteHarness harness;

    private static KbComment comment(int id, MemberIdentity author, String content) {
        return new KbComment(id, 20, null, author, content, false, Instant.EPOCH, null);
    }

    @BeforeEach
    void setup() {
        comments = mock(KbCommentService.class);
        var service = mock(KnowledgeBaseService.class);
        var file = mock(KbFile.class);
        when(file.stationId()).thenReturn(STATION);
        when(service.findFile(20)).thenReturn(Optional.of(file));
        var identities = mock(MemberIdentityFactory.class);
        when(identities.local(STATION, TestSessions.MEMBER_ID)).thenReturn(AUTHOR);
        var federation = mock(KnowledgeBaseFederationService.class);
        when(federation.toCommentResponse(any())).thenAnswer(call -> {
            KbComment c = call.getArgument(0);
            return new CommentResponse(
                    c.id(), null, c.fileId(), null, null, c.author(), null, c.content(), false, null, null, null);
        });
        when(comments.findComment(5)).thenReturn(Optional.of(comment(5, AUTHOR, "alt")));
        when(comments.findComment(6)).thenReturn(Optional.of(comment(6, SOMEBODY_ELSE, "fremd")));
        harness = RouteHarness.serving(new KnowledgeBaseCommentRoutes(
                service, comments, mock(KbAuthorNameService.class), federation, identities));
    }

    @Test
    void theAuthorChangesTheirCommentAndReadsItBackAsStored() {
        var author = harness.as(TestSessions.member(STATION));

        when(comments.findComment(5))
                .thenReturn(Optional.of(comment(5, AUTHOR, "alt")))
                .thenReturn(Optional.of(comment(5, AUTHOR, "neu")));

        var answer = harness.request(
                client -> client.put(PREFIX + "/kb/comments/5", body("{\"content\": \"neu\"}"), author));

        assertEquals("neu", json(answer).path("content").asString());
        verify(comments).updateComment(STATION, 5, TestSessions.MEMBER_ID, null, "neu");
    }

    @Test
    void somebodyElsesCommentIsNeitherChangedNorRemovedWithoutTheRight() {
        var member = harness.as(TestSessions.member(STATION));

        harness.run((server, client) -> {
            assertEquals(
                    Refusal.KB_COMMENT_NOT_YOURS_TO_CHANGE,
                    refusalOf(client.put(PREFIX + "/kb/comments/6", body("{\"content\": \"x\"}"), member)));
            assertEquals(
                    Refusal.KB_COMMENT_NOT_YOURS_TO_DELETE,
                    refusalOf(client.delete(PREFIX + "/kb/comments/6", null, member)));
        });

        verify(comments, never()).deleteComment(anyInt(), anyInt());
    }

    @Test
    void aKnowledgeManagerRemovesAnyCommentOfTheStation() {
        when(comments.deleteComment(STATION, 6)).thenReturn(true);
        var manager = harness.as(TestSessions.member(STATION, StationPermission.KNOWLEDGE_MANAGER));

        var answer = harness.request(client -> client.delete(PREFIX + "/kb/comments/6", null, manager));

        assertEquals(204, answer.code());
    }

    @Test
    void anUnknownCommentIsNotFound() {
        var answer = harness.request(
                client -> client.delete(PREFIX + "/kb/comments/9", null, harness.as(TestSessions.member(STATION))));

        assertEquals(Refusal.KB_COMMENT_NOT_HERE, refusalOf(answer));
    }

    @Test
    void aCommentGoneAfterTheChangeIsReportedSo() {
        var author = harness.as(TestSessions.member(STATION));
        when(comments.findComment(5))
                .thenReturn(Optional.of(comment(5, AUTHOR, "alt")))
                .thenReturn(Optional.empty());

        var answer = harness.request(
                client -> client.put(PREFIX + "/kb/comments/5", body("{\"content\": \"neu\"}"), author));

        assertEquals(Refusal.KB_COMMENT_NOT_HERE_AFTER_CHANGE, refusalOf(answer));
    }
}
