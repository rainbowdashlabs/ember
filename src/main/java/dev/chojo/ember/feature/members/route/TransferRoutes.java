/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.station.service.StationExportService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class TransferRoutes implements Routes {
    private final StationExportService exportService;

    @Inject
    public TransferRoutes(StationExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/version", this::getVersion);
        routes.post(prefix + "/station/transfer/create-token", this::createToken, Roles.MANAGER);
        routes.get(prefix + "/station/transfer/export", this::export, Roles.MANAGER);
        routes.post(prefix + "/transfer/import", this::importStation);
    }

    @OpenApi(
            path = "/api/v1/public/version",
            methods = HttpMethod.GET,
            summary = "Get the application version",
            tags = {"Transfer"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = VersionResponse.class)))
    private void getVersion(Context ctx) {
        ctx.json(new VersionResponse(exportService.getAppVersion()));
    }

    @OpenApi(
            path = "/api/v1/station/transfer/create-token",
            methods = HttpMethod.POST,
            summary = "Create a one-time transfer token for station export",
            description =
                    "Generates a token valid for 24 hours that can be used to import this station's data into another instance.",
            tags = {"Transfer"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TokenResponse.class)))
    private void createToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String token = exportService.createTransferToken(session.stationId());
        ctx.json(new TokenResponse(token, exportService.getAppVersion()));
    }

    @OpenApi(
            path = "/api/v1/station/transfer/export",
            methods = HttpMethod.GET,
            summary = "Export station data",
            description =
                    "Exports all station data as JSON. Excludes GDPR data, account credentials, and session tokens.",
            tags = {"Transfer"},
            responses = @OpenApiResponse(status = "200"))
    private void export(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var data = exportService.exportStation(session.stationId());
        ctx.contentType("application/json");
        ctx.header("Content-Disposition", "attachment; filename=\"station-export.json\"");
        ctx.json(data);
    }

    @OpenApi(
            path = "/api/v1/transfer/import",
            methods = HttpMethod.POST,
            summary = "Import station data from another instance using a transfer token",
            description =
                    "Fetches station data from the source instance using the provided token and host URL. Both instances must be on the same version.",
            tags = {"Transfer"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TransferImportRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void importStation(Context ctx) {
        var request = ctx.bodyAsClass(TransferImportRequest.class);
        if (request.token() == null || request.token().isBlank()) {
            throw new BadRequestResponse("token is required");
        }
        if (request.sourceUrl() == null || request.sourceUrl().isBlank()) {
            throw new BadRequestResponse("sourceUrl is required");
        }

        // Validate token and get station ID
        var stationId = exportService.validateAndConsumeToken(request.token());
        if (stationId.isEmpty()) {
            throw new UnauthorizedResponse("Invalid, expired, or already used transfer token");
        }

        // Version check
        String localVersion = exportService.getAppVersion();
        if (request.sourceVersion() != null && !localVersion.equals(request.sourceVersion())) {
            throw new BadRequestResponse("Version mismatch. Local: " + localVersion + ", Source: "
                    + request.sourceVersion() + ". Both instances must be on the same version.");
        }

        // The actual import would fetch data from sourceUrl and recreate it.
        // For now, return the exported data directly since the token was local.
        var data = exportService.exportStation(stationId.get());
        ctx.json(data);
    }

    public record VersionResponse(String version) {}

    public record TokenResponse(String token, String version) {}

    public record TransferImportRequest(String token, String sourceUrl, String sourceVersion) {}
}
