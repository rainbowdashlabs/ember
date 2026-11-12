/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.route.EventSharingRoutes.FederationShareResponse;
import dev.chojo.ember.feature.events.route.EventSharingRoutes.SetFederationShareRequest;
import dev.chojo.ember.feature.events.route.EventStructureRoutes.FieldDefaultEntry;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.events.service.EventTemplateShareService;
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

import java.util.Arrays;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * What an appointment template hands the appointment editor besides its settings: the starting values
 * of the attendance sheet's fields, and the partner stations the appointment is shared with.
 *
 * <p>Read by whoever may make an appointment from a template, and written by whoever manages
 * templates. Writing a share also needs the right to share appointments, the same right the
 * appointment editor asks for when it writes the share it was handed.
 */
@Singleton
public class EventTemplateSharingRoutes implements Routes {
    private final EventTemplateService templateService;
    private final EventFieldDefaultService fieldDefaultService;
    private final EventTemplateShareService shareService;

    @Inject
    public EventTemplateSharingRoutes(
            EventTemplateService templateService,
            EventFieldDefaultService fieldDefaultService,
            EventTemplateShareService shareService) {
        this.templateService = templateService;
        this.fieldDefaultService = fieldDefaultService;
        this.shareService = shareService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/event-templates/{id}/field-defaults",
                this::getFieldDefaults,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/field-defaults",
                this::setFieldDefaults,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.get(
                prefix + "/event-templates/{id}/federation",
                this::getFederationShare,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.put(
                prefix + "/event-templates/{id}/federation",
                this::setFederationShare,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.delete(
                prefix + "/event-templates/{id}/federation",
                this::removeFederationShare,
                StationPermission.EVENT_MANAGE_TEMPLATE);
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/field-defaults",
            methods = HttpMethod.GET,
            summary = "Get the starting values an event template gives the fields of its attendance sheet",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldDefault[].class)))
    private void getFieldDefaults(Context ctx) {
        int id = requireOwnedTemplate(ctx);
        ctx.json(fieldDefaultService.find(AppointmentOwner.TEMPLATE, id));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/field-defaults",
            methods = HttpMethod.PUT,
            summary = "Set the starting values an event template gives the fields of its attendance sheet",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldDefaultEntry[].class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventFieldDefault[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFieldDefaults(Context ctx) {
        int id = requireOwnedTemplate(ctx);
        var req = ctx.bodyAsClass(FieldDefaultEntry[].class);
        var defaults = Arrays.stream(req)
                .map(entry -> new EventFieldDefault(id, entry.fieldId(), entry.source(), entry.value()))
                .toList();
        fieldDefaultService.set(AppointmentOwner.TEMPLATE, id, defaults);
        ctx.json(fieldDefaultService.find(AppointmentOwner.TEMPLATE, id));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/federation",
            methods = HttpMethod.GET,
            summary = "How the events made from an event template are shared with partner stations",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShareResponse.class)))
    private void getFederationShare(Context ctx) {
        int id = requireOwnedTemplate(ctx);
        ctx.json(shareService
                .find(id)
                .map(found -> new FederationShareResponse(true, found.share().scope(), found.partnerIds()))
                .orElseGet(() -> new FederationShareResponse(false, null, null)));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/federation",
            methods = HttpMethod.PUT,
            summary = "Share the events made from an event template with partner stations",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetFederationShareRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShareResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFederationShare(Context ctx) {
        int id = requireOwnedTemplate(ctx);
        var session = requireFederate(ctx);
        var req = ctx.bodyAsClass(SetFederationShareRequest.class);
        var share = shareService.set(
                session.stationId(), id, req.scope(), req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new FederationShareResponse(true, share.share().scope(), share.partnerIds()));
    }

    @OpenApi(
            path = "/api/v1/event-templates/{id}/federation",
            methods = HttpMethod.DELETE,
            summary = "Stop sharing the events made from an event template",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void removeFederationShare(Context ctx) {
        int id = requireOwnedTemplate(ctx);
        requireFederate(ctx);
        shareService.remove(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private int requireOwnedTemplate(Context ctx) {
        int templateId = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, templateId, templateService::findById, EventTemplate::stationId);
        return templateId;
    }

    private static StationSession requireFederate(Context ctx) {
        var session = StationSession.from(ctx);
        if (!session.hasPermission(StationPermission.EVENTS_FEDERATE)) {
            throw GeneralRefusal.ROUTE_PERMISSION_MISSING.raise();
        }
        return session;
    }
}
