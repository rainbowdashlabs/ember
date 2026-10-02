/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.feature.discovery.service.RemoteStationLogoService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The logos of stations of other instances, as the copies this instance keeps of them, for the public
 * discovery page. Served without signing in, like the logos of this instance's own stations.
 */
@Singleton
public class RemoteStationLogoRoutes implements Routes {
    private final RemoteStationLogoService logos;

    @Inject
    public RemoteStationLogoRoutes(RemoteStationLogoService logos) {
        this.logos = logos;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/discovery/remote/{instance}/{stationUid}/logo", this::getLogo);
    }

    @StationFree("the logo belongs to a station of another instance, which no station of this one owns")
    @OpenApi(
            path = "/api/v1/public/discovery/remote/{instance}/{stationUid}/logo",
            methods = HttpMethod.GET,
            summary = "Get the copy of the logo of a station of another instance",
            tags = {"Discovery"},
            pathParams = {
                @OpenApiParam(name = "instance", type = String.class, required = true),
                @OpenApiParam(name = "stationUid", type = UUID.class, required = true)
            },
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = "image/*")),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getLogo(Context ctx) {
        UUID stationUid = pathUuid(ctx, "stationUid");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        var logo = logos.read(ctx.pathParam("instance"), stationUid, size)
                .orElseThrow(DiscoveryRefusal.REMOTE_LOGO_NOT_HERE::raise);
        ctx.contentType(logo.contentType());
        ctx.result(logo.data());
    }
}
