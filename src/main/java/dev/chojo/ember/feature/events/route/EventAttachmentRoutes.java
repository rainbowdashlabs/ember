/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.events.route.EventOwnership.requireOwnedEvent;

/**
 * The files an event hands over.
 *
 * <p>Reading them asks two questions, and both are asked again on the way to the bytes: may this
 * reader see the event at all, and may they be handed what it keeps back. A file left out of the
 * list is refused when its address is asked for directly, so the list and the download cannot
 * disagree.
 *
 * <p>Writing them is the right that writes the event. Picking which file is attached happens in the
 * media library, so nothing is uploaded here: an attachment is a reference into the station's own
 * library and nothing else.
 */
@Singleton
public class EventAttachmentRoutes implements Routes {

    private final EventAttachmentService attachmentService;
    private final EventCrudService crudService;
    private final EventRestrictionService restrictionService;
    private final MediaLibraryService media;
    private final StationMemberService stationMemberService;

    @Inject
    public EventAttachmentRoutes(
            EventAttachmentService attachmentService,
            EventCrudService crudService,
            EventRestrictionService restrictionService,
            MediaLibraryService media,
            StationMemberService stationMemberService) {
        this.attachmentService = attachmentService;
        this.crudService = crudService;
        this.restrictionService = restrictionService;
        this.media = media;
        this.stationMemberService = stationMemberService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/{id}/attachments", this::list, StationPermission.USER);
        routes.get(prefix + "/events/{id}/attachments/{attachmentId}/file", this::download, StationPermission.USER);
        routes.get(prefix + "/events/{id}/attachments/{attachmentId}/picture", this::picture, StationPermission.USER);
        routes.post(prefix + "/events/{id}/attachments", this::attach, StationPermission.EVENT_EDIT);
        routes.put(prefix + "/events/{id}/attachments/order", this::reorder, StationPermission.EVENT_EDIT);
        routes.put(prefix + "/events/{id}/attachments/{attachmentId}", this::update, StationPermission.EVENT_EDIT);
        routes.delete(prefix + "/events/{id}/attachments/{attachmentId}", this::detach, StationPermission.EVENT_EDIT);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/attachments",
            methods = HttpMethod.GET,
            summary = "List the files an event hands over, as far as the caller may have them",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventAttachment[].class)))
    private void list(Context ctx) {
        var session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "id");
        requireVisibleEvent(ctx, eventId);
        ctx.json(attachmentService.listFor(eventId, session.permissions()));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/attachments/{attachmentId}/file",
            methods = HttpMethod.GET,
            summary = "Download a file an event hands over",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void download(Context ctx) {
        var session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "id");
        requireVisibleEvent(ctx, eventId);

        var attachment = attachmentService
                .findReadable(pathInt(ctx, "attachmentId"), session.permissions())
                .filter(found -> found.eventId() == eventId)
                .orElseThrow(NotFoundResponse::new);

        var file = media.read(session.stationId(), attachment.contentHash()).orElseThrow(NotFoundResponse::new);
        String stored = file.contentType();
        ctx.contentType(SafeInlineMime.safeContentType(stored));
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(
                        SafeInlineMime.isInlineSafe(stored)
                                ? SafeContentDisposition.Disposition.INLINE
                                : SafeContentDisposition.Disposition.ATTACHMENT,
                        attachment.fileName()));
        ctx.result(file.data());
    }

    /** A width that is not a usable number is no width at all, rather than a refusal to answer. */
    private static Integer parseOptionalWidth(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            int value = Integer.parseInt(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The picture of one attachment, for a list that shows what it holds.
     *
     * <p>A picture is the file in miniature, so it is refused wherever the file itself would be: the
     * same two questions, asked the same way, and not the weaker one that lets a reader see the list.
     * A file with no picture answers nothing, and the tile says what kind of file it is instead.
     */
    @OpenApi(
            path = "/api/v1/events/{id}/attachments/{attachmentId}/picture",
            methods = HttpMethod.GET,
            summary = "The picture of an event attachment, where it has one",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "w", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void picture(Context ctx) {
        var session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "id");
        requireVisibleEvent(ctx, eventId);

        var attachment = attachmentService
                .findReadable(pathInt(ctx, "attachmentId"), session.permissions())
                .filter(found -> found.eventId() == eventId)
                .orElseThrow(NotFoundResponse::new);

        var picture = media.readPicture(
                        session.stationId(),
                        attachment.contentHash(),
                        attachment.mimeType(),
                        parseOptionalWidth(ctx.queryParam("w")))
                .orElseThrow(NotFoundResponse::new);
        String stored = picture.contentType();
        ctx.contentType(SafeInlineMime.safeContentType(stored));
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(
                        SafeInlineMime.isInlineSafe(stored)
                                ? SafeContentDisposition.Disposition.INLINE
                                : SafeContentDisposition.Disposition.ATTACHMENT,
                        attachment.fileName()));
        ctx.result(picture.data());
    }

    @OpenApi(
            path = "/api/v1/events/{id}/attachments",
            methods = HttpMethod.POST,
            summary = "Attach a file of the station library to an event",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventAttachment.class)))
    private void attach(Context ctx) {
        var session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "id");
        requireOwnedEvent(crudService, eventId, session);
        var request = ctx.bodyAsClass(AttachmentRequest.class);
        if (request.fileId() == null) throw new BadRequestResponse("fileId is required");
        ctx.status(HttpStatus.CREATED)
                .json(attachmentService.attach(
                        eventId,
                        session.stationId(),
                        request.fileId(),
                        request.label(),
                        request.internal() != null && request.internal()));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/attachments/{attachmentId}",
            methods = HttpMethod.PUT,
            summary = "Write what a file is called and whether it is kept back from the room",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void update(Context ctx) {
        var attachment = requireOwnedAttachment(ctx);
        var request = ctx.bodyAsClass(AttachmentRequest.class);
        boolean internal = request.internal() != null ? request.internal() : attachment.internal();
        if (!attachmentService.update(attachment.id(), request.label(), internal)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/attachments/order",
            methods = HttpMethod.PUT,
            summary = "Write the order the files are handed over in",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentOrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorder(Context ctx) {
        var session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "id");
        requireOwnedEvent(crudService, eventId, session);
        var request = ctx.bodyAsClass(AttachmentOrderRequest.class);
        attachmentService.reorder(eventId, request.attachmentIds() != null ? request.attachmentIds() : List.of());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/attachments/{attachmentId}",
            methods = HttpMethod.DELETE,
            summary = "Take a file off an event, leaving it in the library",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void detach(Context ctx) {
        var attachment = requireOwnedAttachment(ctx);
        if (!attachmentService.detach(attachment.id())) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The event, asserted to be the caller's station's and one they may see.
     *
     * <p>Whoever may write events is let through whatever the event says about who may see it: an
     * editor working on an appointment they restricted to one group still has to be able to open
     * it. A guardian sees what the members they look after see, the same as in the event list.
     */
    private void requireVisibleEvent(Context ctx, int eventId) {
        var session = UserSession.from(ctx);
        requireOwnedEvent(crudService, eventId, session);
        if (session.permissions().contains(StationPermission.EVENT_EDIT)) return;
        var spokenFor = stationMemberService.findSpokenForIds(session);
        if (!restrictionService.canViewAny(eventId, spokenFor, session.permissions())) {
            throw new ForbiddenResponse("This event is not yours to see");
        }
    }

    /**
     * An attachment of an event of the caller's station, named by both ids so an attachment of one
     * event cannot be written through the address of another.
     */
    private EventAttachment requireOwnedAttachment(Context ctx) {
        var session = UserSession.from(ctx);
        int eventId = pathInt(ctx, "id");
        requireOwnedEvent(crudService, eventId, session);
        return attachmentService
                .find(pathInt(ctx, "attachmentId"))
                .filter(attachment -> attachment.eventId() == eventId)
                .orElseThrow(NotFoundResponse::new);
    }

    /**
     * @param fileId   the file in the station library
     * @param label    what a reader sees instead of the file name, or null to use the file name
     * @param internal whether the file is kept back from the room, false where nothing is said
     */
    @OpenApiName("EventAttachmentRequest")
    public record AttachmentRequest(Integer fileId, String label, Boolean internal) {}

    @OpenApiName("EventAttachmentOrderRequest")
    public record AttachmentOrderRequest(List<Integer> attachmentIds) {}
}
