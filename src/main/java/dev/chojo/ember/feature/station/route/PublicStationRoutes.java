/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.station.service.PublicStationInfoService;
import dev.chojo.ember.feature.station.service.PublicStationInfoService.PublicStationInfo;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class PublicStationRoutes implements Routes {
    private final PublicStationInfoService publicInfo;

    @Inject
    public PublicStationRoutes(PublicStationInfoService publicInfo) {
        this.publicInfo = publicInfo;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/station/{stationUid}/info", this::getInfo);
    }

    @OpenApi(
            path = "/api/v1/public/station/{stationUid}/info",
            methods = HttpMethod.GET,
            summary = "Get public information about a station",
            tags = {"Public Station"},
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            description = "An association's own station answers with its wiki alone, and only when the "
                    + "association has put that wiki on the public web.",
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicStationInfo.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getInfo(Context ctx) {
        ctx.json(publicInfo.info(ctx.pathParam("stationUid")));
    }
}
