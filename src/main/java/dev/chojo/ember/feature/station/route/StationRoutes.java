/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StationRoutes implements Routes {
    private final StationService stationService;

    @Inject
    public StationRoutes(StationService stationService) {
        this.stationService = stationService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/stations", this::list, Roles.ADMIN);
        routes.post(prefix + "/stations", this::create, Roles.ADMIN);
        routes.get(prefix + "/stations/{id}", this::get, Roles.ADMIN);
        routes.put(prefix + "/stations/{id}", this::update, Roles.ADMIN);
        routes.delete(prefix + "/stations/{id}", this::delete, Roles.ADMIN);
    }

    @OpenApi(
            path = "/api/v1/stations",
            methods = HttpMethod.GET,
            summary = "List all stations",
            tags = {"Stations"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Station[].class)))
    private void list(Context ctx) {
        ctx.json(stationService.findAll());
    }

    @OpenApi(
            path = "/api/v1/stations",
            methods = HttpMethod.POST,
            summary = "Create a station with optional manager",
            tags = {"Stations"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = StationDetail.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(StationRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        Station station;
        if (!isBlank(request.managerEmail())) {
            station = stationService.createWithManager(request.name(), request.managerEmail());
        } else {
            station = stationService.create(request.name());
        }
        ctx.status(HttpStatus.CREATED).json(toDetail(station));
    }

    @OpenApi(
            path = "/api/v1/stations/{id}",
            methods = HttpMethod.GET,
            summary = "Get a station by ID with manager info",
            tags = {"Stations"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        stationService.findById(id).ifPresentOrElse(station -> ctx.json(toDetail(station)), () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/stations/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a station with optional manager",
            tags = {"Stations"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(StationRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        if (!isBlank(request.managerEmail())) {
            stationService
                    .updateWithManager(id, request.name(), request.managerEmail())
                    .ifPresentOrElse(station -> ctx.json(toDetail(station)), () -> {
                        throw new NotFoundResponse();
                    });
        } else {
            stationService.update(id, request.name()).ifPresentOrElse(station -> ctx.json(toDetail(station)), () -> {
                throw new NotFoundResponse();
            });
        }
    }

    @OpenApi(
            path = "/api/v1/stations/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a station",
            tags = {"Stations"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (stationService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private StationDetail toDetail(Station station) {
        var info = stationService.findManagerInfo(station.id()).orElse(null);
        ManagerDetail manager = info != null
                ? new ManagerDetail(info.email(), info.firstName(), info.lastName(), info.accountReady())
                : null;
        return new StationDetail(station.id(), station.name(), manager);
    }

    public record StationRequest(String name, String managerEmail) {}

    public record StationDetail(int id, String name, ManagerDetail manager) {}

    public record ManagerDetail(String email, String firstName, String lastName, boolean accountReady) {}
}
