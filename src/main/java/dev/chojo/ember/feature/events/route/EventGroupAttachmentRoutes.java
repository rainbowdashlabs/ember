/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.route.EventAttachmentRoutes.AttachmentOrderRequest;
import dev.chojo.ember.feature.events.route.EventAttachmentRoutes.AttachmentRequest;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventGroupGuards;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The files an event group hands over on every part, before each part's own: read on the event group's
 * page by whoever may open it, and written by whoever edits appointments. A part's page lists them as
 * its own files and hands them out through its own address; only the event group takes one off.
 */
@Singleton
public class EventGroupAttachmentRoutes implements Routes {
    private static final String FILES = "/events/groups/{eventGroupId}/attachments";
    private static final String FILE = FILES + "/{attachmentId}";

    private final EventAttachmentService attachments;
    private final EventGroupService eventGroups;
    private final EventGroupGuards readGuards;
    private final MediaLibraryService media;

    @Inject
    public EventGroupAttachmentRoutes(
            EventAttachmentService attachments,
            EventGroupService eventGroups,
            EventGroupGuards readGuards,
            MediaLibraryService media) {
        this.attachments = attachments;
        this.eventGroups = eventGroups;
        this.readGuards = readGuards;
        this.media = media;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + FILES, this::list, StationPermission.USER, StationModule.EVENTS);
        routes.get(prefix + FILE + "/file", this::download, StationPermission.USER, StationModule.EVENTS);
        routes.post(prefix + FILES, this::attach, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(prefix + FILES + "/order", this::reorder, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(prefix + FILE, this::update, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.delete(prefix + FILE, this::detach, StationPermission.EVENT_EDIT, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attachments",
            methods = HttpMethod.GET,
            summary = "List the files an event group hands over on every part, as far as the caller may have them",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventAttachment[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void list(Context ctx) {
        var session = StationSession.from(ctx);
        int eventGroupId =
                readGuards.requireOpen(session, pathInt(ctx, "eventGroupId")).id();
        ctx.json(attachments.listForGroup(eventGroupId, session.user().permissions()));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attachments/{attachmentId}/file",
            methods = HttpMethod.GET,
            summary = "Download a file an event group hands over",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void download(Context ctx) {
        var session = StationSession.from(ctx);
        int eventGroupId =
                readGuards.requireOpen(session, pathInt(ctx, "eventGroupId")).id();
        var attachment = attachments
                .findReadableOfGroup(
                        eventGroupId,
                        pathInt(ctx, "attachmentId"),
                        session.user().permissions())
                .orElseThrow(EventRefusal.EVENT_FILE_NOT_HERE::raise);
        var file = media.read(session.stationId(), attachment.contentHash())
                .orElseThrow(EventRefusal.EVENT_FILE_CONTENT_NOT_HERE::raise);
        FileResponse.send(ctx, file.contentType(), attachment.fileName(), file.data());
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attachments",
            methods = HttpMethod.POST,
            summary = "Attach a file of the station library to an event group",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventAttachment.class)))
    private void attach(Context ctx) {
        var session = StationSession.from(ctx);
        int eventGroupId = ownedEventGroup(ctx, session);
        var request = ctx.bodyAsClass(AttachmentRequest.class);
        if (request.fileId() == null) throw EventRefusal.EVENT_FILE_NOT_CHOSEN.raise();
        ctx.status(HttpStatus.CREATED)
                .json(attachments.attachToGroup(
                        eventGroupId,
                        session.stationId(),
                        request.fileId(),
                        request.label(),
                        Boolean.TRUE.equals(request.internal())));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attachments/order",
            methods = HttpMethod.PUT,
            summary = "Set the order of the files an event group hands over",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentOrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorder(Context ctx) {
        var session = StationSession.from(ctx);
        int eventGroupId = ownedEventGroup(ctx, session);
        var request = ctx.bodyAsClass(AttachmentOrderRequest.class);
        attachments.reorderGroup(eventGroupId, Objects.requireNonNullElse(request.attachmentIds(), List.of()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attachments/{attachmentId}",
            methods = HttpMethod.PUT,
            summary = "Change the label of a file an event group hands over and whether it is kept back",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void update(Context ctx) {
        var attachment = requireOwnedAttachment(ctx);
        var request = ctx.bodyAsClass(AttachmentRequest.class);
        boolean internal = Objects.requireNonNullElse(request.internal(), attachment.internal());
        if (!attachments.updateOfGroup(attachment.id(), request.label(), internal)) {
            throw EventRefusal.EVENT_FILE_NOT_CHANGED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/attachments/{attachmentId}",
            methods = HttpMethod.DELETE,
            summary = "Take a file off an event group, leaving it in the library",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void detach(Context ctx) {
        var attachment = requireOwnedAttachment(ctx);
        if (!attachments.detachFromGroup(attachment.id())) throw EventRefusal.EVENT_FILE_NOT_REMOVED.raise();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private int ownedEventGroup(Context ctx, StationSession session) {
        return eventGroups
                .requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"))
                .id();
    }

    /** A file of an event group of the caller's station, named by both ids. */
    private EventAttachment requireOwnedAttachment(Context ctx) {
        var session = StationSession.from(ctx);
        int eventGroupId = ownedEventGroup(ctx, session);
        return attachments
                .findOfGroup(eventGroupId, pathInt(ctx, "attachmentId"))
                .orElseThrow(EventRefusal.EVENT_FILE_NOT_HERE_ON_WRITE::raise);
    }
}
