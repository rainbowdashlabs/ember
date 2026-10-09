/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.mail.entity.InstanceMailStation;
import dev.chojo.ember.feature.mail.service.InstanceMailGrantService;
import dev.chojo.ember.feature.mail.service.InstanceMailGrantService.InstanceMailBulkGrantRequest;
import dev.chojo.ember.feature.mail.service.InstanceMailGrantService.InstanceMailBulkWithdrawRequest;
import dev.chojo.ember.feature.mail.service.InstanceMailGrantService.InstanceMailGrantRequest;
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

import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The administrator's side of stations sending through the instance's mail providers: who may,
 * and how much each may send a day. Every change asks for a fresh second factor, as every change to
 * the instance's configuration does.
 */
@Singleton
public class InstanceMailGrantRoutes implements Routes {
    private final InstanceMailGrantService grants;

    @Inject
    public InstanceMailGrantRoutes(InstanceMailGrantService grants) {
        this.grants = grants;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/config/mailing/stations", this::list, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/config/mailing/stations/{stationUid}",
                this::station,
                InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/mailing/stations/{stationUid}",
                this::grant,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.delete(
                prefix + "/admin/config/mailing/stations/{stationUid}",
                this::withdraw,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/config/mailing/stations/grant",
                this::grantAll,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/config/mailing/stations/withdraw",
                this::withdrawAll,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/stations",
            methods = HttpMethod.GET,
            summary = "Every station and whether it may send through the instance's mail providers",
            tags = {"Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceMailStation[].class)))
    private void list(Context ctx) {
        ctx.json(grants.stations());
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/stations/{stationUid}",
            methods = HttpMethod.GET,
            summary = "Whether one station may send through the instance's mail providers",
            tags = {"Settings"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceMailStation.class)))
    private void station(Context ctx) {
        ctx.json(grants.station(pathUuid(ctx, "stationUid")));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/stations/{stationUid}",
            methods = HttpMethod.PUT,
            summary = "Let one station send through the instance's mail providers, or change its daily limit there",
            tags = {"Settings"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InstanceMailGrantRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceMailStation.class)))
    private void grant(Context ctx) {
        var request = ctx.bodyAsClass(InstanceMailGrantRequest.class);
        ctx.json(grants.grant(pathUuid(ctx, "stationUid"), request.dailyLimit()));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/stations/{stationUid}",
            methods = HttpMethod.DELETE,
            summary = "Take the instance's mail providers away from one station",
            tags = {"Settings"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceMailStation.class)))
    private void withdraw(Context ctx) {
        ctx.json(grants.withdraw(pathUuid(ctx, "stationUid")));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/stations/grant",
            methods = HttpMethod.POST,
            summary = "Let several stations send through the instance's mail providers",
            tags = {"Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InstanceMailBulkGrantRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceMailStation[].class)))
    private void grantAll(Context ctx) {
        var request = ctx.bodyAsClass(InstanceMailBulkGrantRequest.class);
        ctx.json(grants.grantAll(request.stationUids(), request.dailyLimit()));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/stations/withdraw",
            methods = HttpMethod.POST,
            summary = "Take the instance's mail providers away from several stations",
            tags = {"Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InstanceMailBulkWithdrawRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceMailStation[].class)))
    private void withdrawAll(Context ctx) {
        ctx.json(grants.withdrawAll(
                ctx.bodyAsClass(InstanceMailBulkWithdrawRequest.class).stationUids()));
    }
}
