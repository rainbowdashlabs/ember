/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.repository.StationRepository.StationLogo;
import dev.chojo.ember.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Singleton
public class StationManageRoutes implements Routes {
    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");

    private final StationService stationService;

    @Inject
    public StationManageRoutes(StationService stationService) {
        this.stationService = stationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/manage", this::getStation, Roles.MANAGER);
        routes.put(prefix + "/station/manage", this::updateStation, Roles.MANAGER);
        routes.post(prefix + "/station/manage/logo", this::uploadLogo, Roles.MANAGER);
        routes.get(prefix + "/station/manage/logo", this::getLogo, Roles.LOGIN);
        routes.get(prefix + "/stations/{stationId}/logo", this::getLogoByStation);
        routes.delete(prefix + "/station/manage/logo", this::deleteLogo, Roles.MANAGER);
    }

    @OpenApi(
            path = "/api/v1/station/manage",
            methods = HttpMethod.GET,
            summary = "Get the current station info for management",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationInfo.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        stationService
                .findById(session.stationId())
                .ifPresentOrElse(
                        station -> {
                            boolean hasLogo =
                                    stationService.getLogo(station.id()).isPresent();
                            ctx.json(new StationInfo(
                                    station.id(), station.name(), station.timezone(), station.locale(), hasLogo));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/station/manage",
            methods = HttpMethod.PUT,
            summary = "Update the current station name",
            tags = {"Station Manage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateStationRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationInfo.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(UpdateStationRequest.class);
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestResponse("name is required");
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            try {
                java.time.ZoneId.of(request.timezone());
            } catch (java.time.zone.ZoneRulesException e) {
                throw new BadRequestResponse("Invalid timezone: " + request.timezone());
            }
            stationService.updateTimezone(session.stationId(), request.timezone());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            stationService.updateLocale(session.stationId(), request.locale());
        }
        stationService
                .update(session.stationId(), request.name())
                .ifPresentOrElse(
                        station -> {
                            boolean hasLogo =
                                    stationService.getLogo(station.id()).isPresent();
                            ctx.json(new StationInfo(
                                    station.id(), station.name(), station.timezone(), station.locale(), hasLogo));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/station/manage/logo",
            methods = HttpMethod.POST,
            summary = "Upload station logo (max 2MB, image only)",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadLogo(Context ctx) {
        UserSession session = UserSession.from(ctx);
        UploadedFile file = ctx.uploadedFile("logo");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (file.size() > MAX_LOGO_SIZE) {
            throw new BadRequestResponse("Logo exceeds maximum size of 2 MB");
        }
        String contentType = file.contentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP, SVG");
        }
        try {
            byte[] data = file.content().readAllBytes();
            stationService.setLogo(session.stationId(), data, contentType);
            ctx.json(new MessageResponse("Logo uploaded"));
        } catch (IOException e) {
            throw new BadRequestResponse("Failed to read uploaded file");
        }
    }

    @OpenApi(
            path = "/api/v1/station/manage/logo",
            methods = HttpMethod.GET,
            summary = "Get station logo",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getLogo(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Optional<StationLogo> logoOpt = stationService.getLogo(session.stationId());
        if (logoOpt.isEmpty()) {
            throw new NotFoundResponse("No logo set");
        }
        StationLogo logo = logoOpt.get();
        ctx.contentType(logo.contentType());
        ctx.result(logo.data());
    }

    @OpenApi(
            path = "/api/v1/stations/{stationId}/logo",
            methods = HttpMethod.GET,
            summary = "Get a station's logo by ID",
            tags = {"Station Manage"},
            pathParams = @OpenApiParam(name = "stationId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getLogoByStation(Context ctx) {
        int stationId = ctx.pathParamAsClass("stationId", Integer.class).get();
        Optional<StationLogo> logoOpt = stationService.getLogo(stationId);
        if (logoOpt.isEmpty()) {
            throw new NotFoundResponse("No logo set");
        }
        StationLogo logo = logoOpt.get();
        ctx.contentType(logo.contentType());
        ctx.result(logo.data());
    }

    @OpenApi(
            path = "/api/v1/station/manage/logo",
            methods = HttpMethod.DELETE,
            summary = "Delete station logo",
            tags = {"Station Manage"},
            responses = {@OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class))})
    private void deleteLogo(Context ctx) {
        UserSession session = UserSession.from(ctx);
        stationService.deleteLogo(session.stationId());
        ctx.json(new MessageResponse("Logo deleted"));
    }

    public record UpdateStationRequest(String name, String timezone, String locale) {}

    public record StationInfo(int id, String name, String timezone, String locale, boolean hasLogo) {}
}
