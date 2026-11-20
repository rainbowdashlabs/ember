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
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventGroupPartSettings;
import dev.chojo.ember.feature.events.entity.EventGroupSettings;
import dev.chojo.ember.feature.events.route.EventStructureRoutes.FieldDefaultEntry;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService;
import dev.chojo.ember.feature.events.service.EventGroupSettingsService;
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
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The date settings of an event group (attendance template with its field pre-fills, category and
 * reminders) and what a part of one sets itself, for whoever edits appointments.
 */
@Singleton
public class EventGroupSettingsRoutes implements Routes {
    private static final String SETTINGS = "/events/groups/{eventGroupId}/settings";

    private final EventGroupSettingsService settings;
    private final EventGroupRegistrationService signUps;

    @Inject
    public EventGroupSettingsRoutes(EventGroupSettingsService settings, EventGroupRegistrationService signUps) {
        this.settings = settings;
        this.signUps = signUps;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + SETTINGS, this::get, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(prefix + SETTINGS, this::set, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.get(
                prefix + "/events/{eventId}/group-settings",
                this::ofPart,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
        routes.put(
                prefix + "/events/{eventId}/group-settings/limit",
                this::setPartLimit,
                StationPermission.EVENT_EDIT,
                StationModule.EVENTS);
    }

    /**
     * The places a part of an event group has of its own.
     *
     * @param limit its places, or null to take the event group's places per part
     */
    public record PartLimitRequest(@Nullable Integer limit) {}

    @OpenApi(
            path = "/api/v1/events/{eventId}/group-settings/limit",
            methods = HttpMethod.PUT,
            summary = "Give a part of an event group places of its own, or take its event group's again",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PartLimitRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupPartSettings.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setPartLimit(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(PartLimitRequest.class);
        var part = settings.setPartLimit(session.stationId(), pathInt(ctx, "eventId"), request.limit());
        signUps.moveUp(session.stationId(), part.eventGroupId());
        ctx.json(part);
    }

    /**
     * What an event group is to hand to its parts.
     *
     * @param templateId    the attendance template, or null for none
     * @param categoryId    the category, or null for none
     * @param reminderDays  the days before a part its reminders go out, or null for none
     * @param fieldDefaults the values filled into the sheet of a part taking the template, or null for none
     */
    public record EventGroupSettingsRequest(
            @Nullable Integer templateId,
            @Nullable Integer categoryId,
            @Nullable List<Integer> reminderDays,
            @Nullable List<FieldDefaultEntry> fieldDefaults) {}

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/settings",
            methods = HttpMethod.GET,
            summary = "The attendance template, pre-fills, category and reminders an event group hands to its parts",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSettings.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(settings.find(session.stationId(), pathInt(ctx, "eventGroupId")));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/settings",
            methods = HttpMethod.PUT,
            summary =
                    "Set the attendance template, pre-fills, category and reminders an event group hands to its parts",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupSettingsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupSettings.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void set(Context ctx) {
        StationSession session = StationSession.from(ctx);
        int eventGroupId = pathInt(ctx, "eventGroupId");
        var request = ctx.bodyAsClass(EventGroupSettingsRequest.class);
        var defaults = Objects.requireNonNullElse(request.fieldDefaults(), List.<FieldDefaultEntry>of()).stream()
                .map(entry -> new EventFieldDefault(eventGroupId, entry.fieldId(), entry.source(), entry.value()))
                .toList();
        ctx.json(settings.save(
                session.stationId(),
                eventGroupId,
                new EventGroupSettings(
                        request.templateId(),
                        request.categoryId(),
                        Objects.requireNonNullElse(request.reminderDays(), List.of()),
                        defaults)));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/group-settings",
            methods = HttpMethod.GET,
            summary = "What a part of an event group sets itself and what it takes over from the event group",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupPartSettings.class)),
                @OpenApiResponse(status = "204")
            })
    private void ofPart(Context ctx) {
        StationSession session = StationSession.from(ctx);
        settings.ofPart(session.stationId(), pathInt(ctx, "eventId"))
                .ifPresentOrElse(ctx::json, () -> ctx.status(HttpStatus.NO_CONTENT));
    }
}
