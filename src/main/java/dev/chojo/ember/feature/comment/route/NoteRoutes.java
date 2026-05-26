/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.comment.entity.EntityNote;
import dev.chojo.ember.feature.comment.entity.NoteEntityType;
import dev.chojo.ember.feature.comment.entity.NoteVersion;
import dev.chojo.ember.feature.comment.service.NoteService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * HTTP route definitions for entity notes.
 * Provides endpoints for reading and updating notes with version history.
 */
@Singleton
public class NoteRoutes implements Routes {
    private final NoteService noteService;

    @Inject
    public NoteRoutes(NoteService noteService) {
        this.noteService = noteService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/notes/{entityType}/{entityId}", this::getNote, Roles.TEAM);
        routes.put(prefix + "/notes/{entityType}/{entityId}", this::updateNote, Roles.TEAM);
        routes.get(prefix + "/notes/{entityType}/{entityId}/versions", this::listVersions, Roles.TEAM);
    }

    private void getNote(Context ctx) {
        var entityType = NoteEntityType.valueOf(ctx.pathParam("entityType").toUpperCase());
        int entityId = ctx.pathParamAsClass("entityId", Integer.class).get();
        var note = noteService.findNote(entityType, entityId);
        if (note.isEmpty()) {
            ctx.json(new NoteResponse(null, entityType, entityId, "", null, null));
        } else {
            ctx.json(toResponse(note.get()));
        }
    }

    private void updateNote(Context ctx) {
        var entityType = NoteEntityType.valueOf(ctx.pathParam("entityType").toUpperCase());
        int entityId = ctx.pathParamAsClass("entityId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(UpdateNoteRequest.class);
        if (request.content() == null) {
            throw new BadRequestResponse("content is required");
        }
        var note = noteService.updateNote(
                entityType,
                entityId,
                session.stationId(),
                request.content(),
                session.member().id());
        ctx.json(toResponse(note));
    }

    private void listVersions(Context ctx) {
        var entityType = NoteEntityType.valueOf(ctx.pathParam("entityType").toUpperCase());
        int entityId = ctx.pathParamAsClass("entityId", Integer.class).get();
        var note = noteService.findNote(entityType, entityId).orElseThrow(NotFoundResponse::new);
        var versions = noteService.findVersions(note.id());
        ctx.json(versions.stream().map(this::toVersionResponse).toList());
    }

    private NoteResponse toResponse(EntityNote note) {
        return new NoteResponse(
                note.id(), note.entityType(), note.entityId(), note.content(), note.updatedBy(), note.updatedAt());
    }

    private NoteVersionResponse toVersionResponse(NoteVersion version) {
        return new NoteVersionResponse(
                version.id(), version.noteId(), version.diffPatch(), version.authorId(), version.createdAt());
    }

    /**
     * Request body for updating a note.
     */
    public record UpdateNoteRequest(String content) {}

    /**
     * API response representing a note.
     */
    public record NoteResponse(
            Integer id,
            NoteEntityType entityType,
            int entityId,
            String content,
            Integer updatedBy,
            Instant updatedAt) {}

    /**
     * API response representing a note version.
     */
    public record NoteVersionResponse(int id, int noteId, String diffPatch, int authorId, Instant createdAt) {}
}
