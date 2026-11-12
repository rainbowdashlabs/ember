/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.route.EventAttachmentRoutes.AttachmentOrderRequest;
import dev.chojo.ember.feature.events.route.EventAttachmentRoutes.AttachmentRequest;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
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
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * The files an appointment template hands every appointment made from it.
 *
 * <p>Written as they change, like an appointment's own files, and read only by whoever edits the
 * template: nobody downloads a file from a template, they get it from the appointment.
 */
@Singleton
public class EventTemplateAttachmentRoutes implements Routes {
    private final EventAttachmentService attachmentService;
    private final EventTemplateService templateService;

    @Inject
    public EventTemplateAttachmentRoutes(
            EventAttachmentService attachmentService, EventTemplateService templateService) {
        this.attachmentService = attachmentService;
        this.templateService = templateService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/event-templates/{id}/attachments",
                this::list,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.post(
                prefix + "/event-templates/{id}/attachments", this::attach, StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/attachments/order",
                this::reorder,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/attachments/{attachmentId}",
                this::update,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.delete(
                prefix + "/event-templates/{id}/attachments/{attachmentId}",
                this::detach,
                StationPermission.EVENT_MANAGE_TEMPLATE);
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/attachments",
            methods = HttpMethod.GET,
            summary = "List the files an event template hands every event made from it",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventAttachment[].class)))
    private void list(Context ctx) {
        int templateId = requireOwnedTemplate(ctx);
        ctx.json(attachmentService.listAll(AppointmentOwner.TEMPLATE, templateId));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/attachments",
            methods = HttpMethod.POST,
            summary = "Attach a file of the station library to an event template",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = EventAttachment.class)))
    private void attach(Context ctx) {
        int templateId = requireOwnedTemplate(ctx);
        var request = ctx.bodyAsClass(AttachmentRequest.class);
        if (request.fileId() == null) throw EventRefusal.EVENT_FILE_NOT_CHOSEN.raise();
        ctx.status(HttpStatus.CREATED)
                .json(attachmentService.attach(
                        AppointmentOwner.TEMPLATE,
                        templateId,
                        StationSession.from(ctx).stationId(),
                        request.fileId(),
                        request.label(),
                        Boolean.TRUE.equals(request.internal())));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/attachments/{attachmentId}",
            methods = HttpMethod.PUT,
            summary = "Write what a template's file is called and whether it is kept back from the room",
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
        boolean internal = Objects.requireNonNullElse(request.internal(), attachment.internal());
        if (!attachmentService.update(AppointmentOwner.TEMPLATE, attachment.id(), request.label(), internal)) {
            throw EventRefusal.EVENT_FILE_NOT_CHANGED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/attachments/order",
            methods = HttpMethod.PUT,
            summary = "Write the order a template hands its files over in",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AttachmentOrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorder(Context ctx) {
        int templateId = requireOwnedTemplate(ctx);
        var request = ctx.bodyAsClass(AttachmentOrderRequest.class);
        attachmentService.reorder(
                AppointmentOwner.TEMPLATE,
                templateId,
                request.attachmentIds() != null ? request.attachmentIds() : List.of());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/attachments/{attachmentId}",
            methods = HttpMethod.DELETE,
            summary = "Take a file off an event template, leaving it in the library",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "attachmentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void detach(Context ctx) {
        var attachment = requireOwnedAttachment(ctx);
        if (!attachmentService.detach(AppointmentOwner.TEMPLATE, attachment.id())) {
            throw EventRefusal.EVENT_FILE_NOT_REMOVED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private int requireOwnedTemplate(Context ctx) {
        int templateId = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, templateId, templateService::findById, EventTemplate::stationId);
        return templateId;
    }

    /**
     * An attachment of a template of the caller's station, named by both ids so an attachment of one
     * template cannot be written through the address of another.
     */
    private EventAttachment requireOwnedAttachment(Context ctx) {
        int templateId = requireOwnedTemplate(ctx);
        return attachmentService
                .find(AppointmentOwner.TEMPLATE, pathInt(ctx, "attachmentId"))
                .filter(attachment -> attachment.eventId() == templateId)
                .orElseThrow(EventRefusal.EVENT_FILE_NOT_HERE_ON_WRITE::raise);
    }
}
