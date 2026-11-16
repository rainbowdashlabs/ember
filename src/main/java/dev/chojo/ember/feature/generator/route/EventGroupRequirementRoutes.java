/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.service.EventGroupGuards;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.generator.entity.RequiredTemplate;
import dev.chojo.ember.feature.generator.route.AppointmentDocumentRoutes.RequirementsRequest;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
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
 * The documents an event group asks every part's participants to bring. The event group's page lists
 * them for whoever may open it; each part's page asks for them before its own and hands out the copies,
 * since a copy belongs to one date. Whoever edits appointments sets them.
 */
@Singleton
public class EventGroupRequirementRoutes implements Routes {
    private static final String REQUIREMENTS = "/events/groups/{eventGroupId}/document-requirements";

    private final EventRequirementService requirements;
    private final EventGroupService eventGroups;
    private final EventGroupGuards readGuards;

    @Inject
    public EventGroupRequirementRoutes(
            EventRequirementService requirements, EventGroupService eventGroups, EventGroupGuards readGuards) {
        this.requirements = requirements;
        this.eventGroups = eventGroups;
        this.readGuards = readGuards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + REQUIREMENTS, this::get, StationPermission.USER, StationModule.EVENTS);
        routes.put(prefix + REQUIREMENTS, this::set, StationPermission.EVENT_EDIT, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/document-requirements",
            methods = HttpMethod.GET,
            summary = "The documents an event group asks the participants of every part to bring",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequiredTemplate[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        var session = StationSession.from(ctx);
        int eventGroupId =
                readGuards.requireOpen(session, pathInt(ctx, "eventGroupId")).id();
        ctx.json(requirements.forEventGroup(eventGroupId));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/document-requirements",
            methods = HttpMethod.PUT,
            summary = "Set the documents an event group asks the participants of every part to bring",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RequirementsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequiredTemplate[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void set(Context ctx) {
        var session = StationSession.from(ctx);
        int eventGroupId = eventGroups
                .requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"))
                .id();
        var request = ctx.bodyAsClass(RequirementsRequest.class);
        List<Integer> templateIds = Objects.requireNonNullElse(request.templateIds(), List.of());
        ctx.json(requirements.setForEventGroup(session.owner(), eventGroupId, templateIds));
    }
}
