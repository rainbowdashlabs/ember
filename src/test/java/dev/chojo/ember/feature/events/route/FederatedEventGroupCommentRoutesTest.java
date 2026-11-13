/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.events.service.EventGroupCommentFederation;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The thread of a partner's event group over HTTP: a member of this station reads and writes it as
 * themselves, at the partner and event group the path names, and an empty comment never leaves.
 */
class FederatedEventGroupCommentRoutesTest {
    private static final int STATION_ID = 3;
    private static final UUID HOLDER = UUID.randomUUID();
    private static final String COMMENTS = PREFIX + "/federated/" + HOLDER + "/events/groups/70/comments";

    private EventGroupCommentFederation comments;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        comments = mock(EventGroupCommentFederation.class);
        var answer =
                new CommentResponse(5, null, null, null, null, null, "Mara", "Hallo", false, Instant.now(), null, null);
        when(comments.listAtPartner(STATION_ID, HOLDER, 70)).thenReturn(List.of(answer));
        when(comments.createAtPartner(eq(STATION_ID), eq(HOLDER), eq(70), any(), anyString(), any(), anyString()))
                .thenReturn(answer);
        when(comments.updateAtPartner(eq(STATION_ID), eq(HOLDER), eq(70), eq(5), any(), anyString()))
                .thenReturn(answer);
        harness = RouteHarness.serving(new FederatedEventGroupCommentRoutes(comments));
    }

    private Response call(String method, String path, String json, StationPermission... permissions) {
        var session = harness.as(TestSessions.member(STATION_ID, permissions));
        return harness.request(client -> switch (method) {
            case "GET" -> client.get(path, session);
            case "POST" -> client.post(path, body(json), session);
            case "PUT" -> client.put(path, body(json), session);
            default -> client.delete(path, null, session);
        });
    }

    @Test
    void aMemberReadsTheThreadAtThePartner() {
        var listed = json(call("GET", COMMENTS, null, StationPermission.USER));

        assertEquals("Hallo", listed.get(0).path("content").asString());
    }

    @Test
    void aMemberWritesAsThemselves() {
        assertEquals(
                201,
                call("POST", COMMENTS, "{\"content\": \"Hallo\"}", StationPermission.USER)
                        .code());

        var self = TestSessions.member(STATION_ID).member();
        verify(comments)
                .createAtPartner(
                        eq(STATION_ID), eq(HOLDER), eq(70), eq(self.uid()), anyString(), isNull(), eq("Hallo"));
    }

    @Test
    void aMemberChangesAndRemovesAsThemselves() {
        var self = TestSessions.member(STATION_ID).member();

        assertEquals(
                200,
                call("PUT", COMMENTS + "/5", "{\"content\": \"neu\"}", StationPermission.USER)
                        .code());
        assertEquals(
                204,
                call("DELETE", COMMENTS + "/5", null, StationPermission.USER).code());

        verify(comments).updateAtPartner(STATION_ID, HOLDER, 70, 5, self.uid(), "neu");
        verify(comments).deleteAtPartner(STATION_ID, HOLDER, 70, 5, self.uid());
    }

    @Test
    void anEmptyCommentNeverLeaves() {
        assertEquals(
                EventRefusal.FEDERATED_COMMENT_NEEDS_TEXT,
                refusalOf(call("POST", COMMENTS, "{\"content\": \" \"}", StationPermission.USER)));
        assertEquals(
                EventRefusal.FEDERATED_COMMENT_CHANGE_NEEDS_TEXT,
                refusalOf(call("PUT", COMMENTS + "/5", "{\"content\": \"\"}", StationPermission.USER)));
        verify(comments, never()).createAtPartner(anyInt(), any(), anyInt(), any(), anyString(), any(), anyString());
    }

    @Test
    void aSessionWithoutTheUserRightIsTurnedAway() {
        assertEquals(403, call("GET", COMMENTS, null).code());
        assertEquals(403, call("POST", COMMENTS, "{\"content\": \"x\"}").code());
    }
}
