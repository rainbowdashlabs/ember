/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventPlaceService;
import dev.chojo.ember.feature.events.service.EventPlaceService.PlaceDraft;
import dev.chojo.ember.feature.maps.entity.GeocodeAnswer;
import dev.chojo.ember.feature.maps.entity.StationPlace;
import dev.chojo.ember.feature.maps.service.GeocodingRateLimiter;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The map pins of the places the station's appointments name: those who edit appointments find,
 * correct and store a pin per place text; everybody who sees an appointment sees the pins of its
 * place fields.
 */
@Singleton
public class EventPlaceRoutes implements Routes {

    private final EventPlaceService placeService;
    private final EventFieldService fieldService;
    private final EventVisibility visibility;
    private final GeocodingRateLimiter rateLimiter;

    @Inject
    public EventPlaceRoutes(
            EventPlaceService placeService,
            EventFieldService fieldService,
            EventVisibility visibility,
            GeocodingRateLimiter rateLimiter) {
        this.placeService = placeService;
        this.fieldService = fieldService;
        this.visibility = visibility;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/places", this::find, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.put(prefix + "/station/places", this::save, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.delete(prefix + "/station/places", this::remove, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.post(
                prefix + "/station/places/geocode", this::geocode, StationPermission.EVENT_EDIT, StationModule.EVENTS);
        routes.get(prefix + "/events/{id}/places", this::pinsOfEvent, StationPermission.USER, StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/station/places",
            methods = HttpMethod.GET,
            summary = "The stored pin of a place text",
            tags = {"Events"},
            queryParams = @OpenApiParam(name = "text", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PlaceAnswer.class)))
    private void find(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(new PlaceAnswer(
                placeService.find(session.stationId(), ctx.queryParam("text")).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/station/places",
            methods = HttpMethod.PUT,
            summary = "Store the pin of a place text",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PlaceDraft.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationPlace.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void save(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(placeService.save(session.stationId(), ctx.bodyAsClass(PlaceDraft.class)));
    }

    @OpenApi(
            path = "/api/v1/station/places",
            methods = HttpMethod.DELETE,
            summary = "Take the pin off a place text",
            tags = {"Events"},
            queryParams = @OpenApiParam(name = "text", type = String.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void remove(Context ctx) {
        placeService.remove(StationSession.from(ctx).stationId(), ctx.queryParam("text"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station/places/geocode",
            methods = HttpMethod.POST,
            summary = "Find where a place text lies",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PlaceSearch.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = GeocodeAnswer.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "503", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void geocode(Context ctx) {
        var session = StationSession.from(ctx);
        RateLimits.enforce(MapRefusal.GEOCODING_TOO_OFTEN, rateLimiter.tryAcquire(session.accountId()));
        var search = ctx.bodyAsClass(PlaceSearch.class);
        ctx.json(new GeocodeAnswer(
                placeService.locate(session.stationId(), search.text()).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/places",
            methods = HttpMethod.GET,
            summary = "The pins of an appointment's place fields",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "date", type = String.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationPlace[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void pinsOfEvent(Context ctx) {
        var session = StationSession.from(ctx);
        int id = pathInt(ctx, "id");
        visibility.requireVisibleEvent(session, id);
        var fields = fieldService.findByEvent(id, EventStructureRoutes.askedDate(ctx));
        ctx.json(placeService.pinsOf(session.stationId(), fields));
    }

    /**
     * Request payload for looking a place text up on the map.
     *
     * @param text the place text, as the field holds it
     */
    public record PlaceSearch(@Nullable String text) {}

    /**
     * Response payload of a stored pin lookup.
     *
     * @param place the stored pin, or null when the place text has none
     */
    public record PlaceAnswer(@Nullable StationPlace place) {}
}
