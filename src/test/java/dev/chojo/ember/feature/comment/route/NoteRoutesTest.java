/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.CommentRefusal;
import dev.chojo.ember.feature.comment.entity.EntityNote;
import dev.chojo.ember.feature.comment.entity.NoteEntityType;
import dev.chojo.ember.feature.comment.service.NoteService;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The manager notes on an event group over HTTP: whoever edits appointments reads and writes them,
 * as on an appointment, and the right to member notes does not reach them.
 */
class NoteRoutesTest {
    private static final int STATION_ID = 3;
    private static final int EVENT_GROUP_ID = 70;
    private static final String NOTE = PREFIX + "/notes/event_group/" + EVENT_GROUP_ID;

    private NoteService notes;
    private RouteHarness harness;

    @BeforeEach
    void serve() {
        notes = mock(NoteService.class);
        when(notes.findNote(NoteEntityType.EVENT_GROUP, EVENT_GROUP_ID, STATION_ID))
                .thenReturn(Optional.of(note("Schlüssel bei Lea")));
        when(notes.updateNote(
                        eq(NoteEntityType.EVENT_GROUP), eq(EVENT_GROUP_ID), eq(STATION_ID), anyString(), anyInt()))
                .thenAnswer(call -> note(call.getArgument(3)));
        harness = RouteHarness.serving(new NoteRoutes(notes));
    }

    private static EntityNote note(String content) {
        return new EntityNote(1, NoteEntityType.EVENT_GROUP, EVENT_GROUP_ID, STATION_ID, content, 11, Instant.now());
    }

    private Response get(StationPermission... permissions) {
        return harness.request(client -> client.get(NOTE, harness.as(TestSessions.member(STATION_ID, permissions))));
    }

    private Response put(String content, StationPermission... permissions) {
        return harness.request(client -> client.put(
                NOTE,
                body("{\"content\": \"%s\"}".formatted(content)),
                harness.as(TestSessions.member(STATION_ID, permissions))));
    }

    @Test
    void anAppointmentEditorReadsAndWritesTheNote() {
        assertEquals(
                "Schlüssel bei Lea",
                json(get(StationPermission.EVENT_EDIT)).path("content").asString());
        assertEquals(
                "Halle reserviert",
                json(put("Halle reserviert", StationPermission.EVENT_EDIT))
                        .path("content")
                        .asString());
    }

    @Test
    void theRightToMemberNotesDoesNotReachAnEventGroup() {
        assertEquals(CommentRefusal.NOTES_NOT_YOURS, refusalOf(get(StationPermission.MEMBER_NOTES)));
        assertEquals(CommentRefusal.NOTES_NOT_YOURS, refusalOf(put("x", StationPermission.MEMBER_NOTES)));
        verify(notes, never()).updateNote(eq(NoteEntityType.EVENT_GROUP), anyInt(), anyInt(), anyString(), anyInt());
    }

    @Test
    void aMemberWithoutEitherRightIsTurnedAway() {
        assertEquals(403, get().code());
        assertEquals(403, put("x").code());
    }
}
