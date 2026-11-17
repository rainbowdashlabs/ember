/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MapRefusal;
import dev.chojo.ember.feature.maps.entity.GeocodeAnswer;
import dev.chojo.ember.feature.maps.service.GeocodingRateLimiter;
import dev.chojo.ember.feature.station.service.StationLocationService;
import dev.chojo.ember.feature.station.service.StationLocationService.AddressSearch;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Finding the station's address on the map. Whoever may set where the station is may look its
 * address up; the answer only moves the pin in the form and is never saved here.
 */
@Singleton
public class StationLocationLookupRoutes implements Routes {

    private final StationLocationService locationService;
    private final GeocodingRateLimiter rateLimiter;

    @Inject
    public StationLocationLookupRoutes(StationLocationService locationService, GeocodingRateLimiter rateLimiter) {
        this.locationService = locationService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/station/location/geocode", this::geocode, StationPermission.STATION_GENERAL);
    }

    @OpenApi(
            path = "/api/v1/station/location/geocode",
            methods = HttpMethod.POST,
            summary = "Find where the station's address lies",
            tags = {"Station Manage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AddressSearch.class)),
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
        var search = ctx.bodyAsClass(AddressSearch.class);
        ctx.json(new GeocodeAnswer(locationService.locate(search).orElse(null)));
    }
}
