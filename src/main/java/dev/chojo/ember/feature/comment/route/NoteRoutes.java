/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.entity.EntityNote;
import dev.chojo.ember.feature.comment.entity.NoteEntityType;
import dev.chojo.ember.feature.comment.entity.NoteVersion;
import dev.chojo.ember.feature.comment.service.NoteService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * HTTP route definitions for entity notes.
 * Provides endpoints for reading and updating notes with version history.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class NoteRoutes implements Routes {
    private final NoteService noteService;

    @Inject
    public NoteRoutes(NoteService noteService) {
        this.noteService = noteService;
    }

    private static StationPermission requiredPermission(NoteEntityType entityType) {
        return entityType == NoteEntityType.EVENT ? StationPermission.EVENT_EDIT : StationPermission.MEMBER_NOTES;
    }

    private static void requireNoteAccess(UserSession session, NoteEntityType entityType) {
        StationPermission required = requiredPermission(entityType);
        if (!session.hasPermission(required)) {
            throw new ForbiddenResponse("Missing required permission: " + required.name());
        }
    }

    private NoteAccess resolveAccess(Context ctx) {
        var entityType = NoteEntityType.valueOf(ctx.pathParam("entityType").toUpperCase());
        int entityId = pathInt(ctx, "entityId");
        UserSession session = UserSession.from(ctx);
        requireNoteAccess(session, entityType);
        return new NoteAccess(entityType, entityId, session);
    }

    private record NoteAccess(NoteEntityType entityType, int entityId, UserSession session) {}

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Note routes are shared by multiple entity types with different permission requirements
        // (EVENT notes require EVENT_EDIT, ITEM / MEMBER notes require MEMBER_NOTES). Pass both
        // roles so callers with either pass the route-level gate; the per-entity-type check is
        // refined inside each handler via {@link #requireNoteAccess}.
        routes.get(
                prefix + "/notes/{entityType}/{entityId}",
                this::getNote,
                StationPermission.MEMBER_NOTES,
                StationPermission.EVENT_EDIT);
        routes.put(
                prefix + "/notes/{entityType}/{entityId}",
                this::updateNote,
                StationPermission.MEMBER_NOTES,
                StationPermission.EVENT_EDIT);
        routes.get(
                prefix + "/notes/{entityType}/{entityId}/versions",
                this::listVersions,
                StationPermission.MEMBER_NOTES,
                StationPermission.EVENT_EDIT);
    }

    @OpenApi(
            path = "/api/v1/notes/{entityType}/{entityId}",
            methods = HttpMethod.GET,
            summary = "Get a note for an entity",
            tags = {"Notes"},
            pathParams = {
                @OpenApiParam(name = "entityType", type = String.class, required = true),
                @OpenApiParam(name = "entityId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NoteResponse.class)))
    private void getNote(Context ctx) {
        var access = resolveAccess(ctx);
        var note = noteService.findNote(
                access.entityType(), access.entityId(), access.session().stationId());
        if (note.isEmpty()) {
            ctx.json(new NoteResponse(null, access.entityType(), access.entityId(), "", null, null));
        } else {
            ctx.json(toResponse(note.get()));
        }
    }

    @OpenApi(
            path = "/api/v1/notes/{entityType}/{entityId}",
            methods = HttpMethod.PUT,
            summary = "Update a note for an entity",
            tags = {"Notes"},
            pathParams = {
                @OpenApiParam(name = "entityType", type = String.class, required = true),
                @OpenApiParam(name = "entityId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateNoteRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NoteResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateNote(Context ctx) {
        var access = resolveAccess(ctx);
        var request = ctx.bodyAsClass(UpdateNoteRequest.class);
        if (request.content() == null) {
            throw new BadRequestResponse("content is required");
        }
        var note = noteService.updateNote(
                access.entityType(),
                access.entityId(),
                access.session().stationId(),
                request.content(),
                access.session().member().id());
        ctx.json(toResponse(note));
    }

    @OpenApi(
            path = "/api/v1/notes/{entityType}/{entityId}/versions",
            methods = HttpMethod.GET,
            summary = "List version history for a note",
            tags = {"Notes"},
            pathParams = {
                @OpenApiParam(name = "entityType", type = String.class, required = true),
                @OpenApiParam(name = "entityId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = NoteVersionResponse[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listVersions(Context ctx) {
        var access = resolveAccess(ctx);
        var note = noteService
                .findNote(
                        access.entityType(), access.entityId(), access.session().stationId())
                .orElseThrow(NotFoundResponse::new);
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
