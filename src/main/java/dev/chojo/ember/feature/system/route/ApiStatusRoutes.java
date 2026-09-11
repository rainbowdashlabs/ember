/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.system.service.ApiRequestLogger;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Locale;

/**
 * Admin routes for API request monitoring - response times, status codes, endpoint stats.
 */
@Singleton
public class ApiStatusRoutes implements Routes {

    private final ApiRequestLogger requestLogger;

    @Inject
    public ApiStatusRoutes(ApiRequestLogger requestLogger) {
        this.requestLogger = requestLogger;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/api-status/slowest", this::slowest, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/api-status/fastest", this::fastest, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/api-status/failing", this::failing, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/api-status/status-breakdown", this::statusBreakdown, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/api-status/hourly", this::hourly, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/api-status/endpoint", this::endpoint, InstancePermission.ADMINISTRATOR);
    }

    /**
     * One endpoint on its own, which is what the list links to.
     *
     * <p>The path asked for is the reduced form the list shows, placeholders and all, because that is what
     * every row was recorded under.
     */
    @OpenApi(
            path = "/api/v1/admin/api-status/endpoint",
            methods = HttpMethod.GET,
            summary = "Get everything known about one endpoint",
            tags = {"API Status"},
            queryParams = {
                @OpenApiParam(name = "method", required = true),
                @OpenApiParam(name = "path", required = true)
            },
            responses = {
                @OpenApiResponse(
                        status = "200",
                        content = @OpenApiContent(from = ApiRequestLogger.EndpointDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void endpoint(Context ctx) {
        String method = ctx.queryParam("method");
        String path = ctx.queryParam("path");
        if (method == null || method.isBlank() || path == null || path.isBlank()) {
            throw new BadRequestResponse("Name the method and the path of the endpoint you want");
        }
        ctx.json(requestLogger.getEndpointDetail(method.trim().toUpperCase(Locale.ROOT), path.trim()));
    }

    @OpenApi(
            path = "/api/v1/admin/api-status/slowest",
            methods = HttpMethod.GET,
            summary = "Get the slowest API endpoints",
            tags = {"API Status"},
            queryParams = @OpenApiParam(name = "limit", type = Integer.class),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ApiRequestLogger.EndpointStats[].class)))
    private void slowest(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(requestLogger.getSlowestEndpoints(limit));
    }

    @OpenApi(
            path = "/api/v1/admin/api-status/fastest",
            methods = HttpMethod.GET,
            summary = "Get the fastest API endpoints",
            tags = {"API Status"},
            queryParams = @OpenApiParam(name = "limit", type = Integer.class),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ApiRequestLogger.EndpointStats[].class)))
    private void fastest(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(requestLogger.getFastestEndpoints(limit));
    }

    @OpenApi(
            path = "/api/v1/admin/api-status/failing",
            methods = HttpMethod.GET,
            summary = "Get the most failing API endpoints",
            tags = {"API Status"},
            queryParams = @OpenApiParam(name = "limit", type = Integer.class),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ApiRequestLogger.EndpointStats[].class)))
    private void failing(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(requestLogger.getMostFailingEndpoints(limit));
    }

    @OpenApi(
            path = "/api/v1/admin/api-status/status-breakdown",
            methods = HttpMethod.GET,
            summary = "Get status code breakdown by endpoint",
            tags = {"API Status"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ApiRequestLogger.StatusBreakdown[].class)))
    private void statusBreakdown(Context ctx) {
        ctx.json(requestLogger.getStatusBreakdown());
    }

    @OpenApi(
            path = "/api/v1/admin/api-status/hourly",
            methods = HttpMethod.GET,
            summary = "Get hourly request statistics",
            tags = {"API Status"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ApiRequestLogger.HourlyStats[].class)))
    private void hourly(Context ctx) {
        ctx.json(requestLogger.getHourlyStats());
    }
}
