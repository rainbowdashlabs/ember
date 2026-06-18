/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Routes for station data transfer including token-authenticated public export endpoints
 * for paginated table data and admin import/progress endpoints.
 */
@Singleton
public class TransferRoutes implements Routes {
    private final StationExportService exportService;
    private final StationImportService importService;

    @Inject
    public TransferRoutes(StationExportService exportService, StationImportService importService) {
        this.exportService = exportService;
        this.importService = importService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/version", this::getVersion);
        routes.post(
                prefix + "/station/transfer/create-token", this::createToken, StationPermission.STATION_IMPORT_EXPORT);

        // Token-authenticated export (public, for remote import)
        routes.get(prefix + "/public/transfer/{token}/tables", this::tokenListTables);
        routes.get(prefix + "/public/transfer/{token}/{table}", this::tokenExportTable);

        // Import (async, fetches from remote)
        routes.post(prefix + "/admin/transfer/import", this::startImport, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/transfer/import/{stationId}/progress",
                this::importProgress,
                InstancePermission.ADMINISTRATOR);
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
            tags = {"Transfer"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TokenResponse.class)))
    private void createToken(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String token = exportService.createTransferToken(session.stationId());
        ctx.json(new TokenResponse(token, exportService.getAppVersion()));
    }

    // -- Token-authenticated export --

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/tables",
            methods = HttpMethod.GET,
            summary = "List export tables using a transfer token",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "token", required = true),
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "403")})
    private void tokenListTables(Context ctx) {
        String token = ctx.pathParam("token");
        exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        ctx.json(new TablesResponse(
                exportService.getTableOrder(), exportService.getSchemaHash(), exportService.getAppVersion()));
    }

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/{table}",
            methods = HttpMethod.GET,
            summary = "Export a single table page using a transfer token",
            description = "Supports pagination via offset and limit query parameters. Default limit is 500.",
            tags = {"Transfer"},
            pathParams = {@OpenApiParam(name = "token", required = true), @OpenApiParam(name = "table", required = true)
            },
            queryParams = {
                @OpenApiParam(name = "offset", type = Integer.class),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "403"),
                @OpenApiResponse(status = "400")
            })
    private void tokenExportTable(Context ctx) {
        String token = ctx.pathParam("token");
        int stationId =
                exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        String table = ctx.pathParam("table");
        if (!exportService.getTableOrder().contains(table)) {
            throw new BadRequestResponse("Unknown table: " + table);
        }
        int offset = ctx.queryParamAsClass("offset", Integer.class).getOrDefault(0);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(500);
        ctx.json(exportService.exportTable(stationId, table, offset, limit));
    }

    // -- Import --

    @OpenApi(
            path = "/api/v1/admin/transfer/import",
            methods = HttpMethod.POST,
            summary = "Start a station import from a remote instance",
            description =
                    "Provide the source instance URL and a transfer token. The backend fetches tables one by one and imports them. Poll the progress endpoint to track status.",
            tags = {"Transfer"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ImportRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ImportStartResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void startImport(Context ctx) {
        var req = ctx.bodyAsClass(ImportRequest.class);
        if (req.sourceUrl() == null || req.sourceUrl().isBlank()) {
            throw new BadRequestResponse("sourceUrl is required");
        }
        if (req.token() == null || req.token().isBlank()) {
            throw new BadRequestResponse("token is required");
        }
        String sourceUrl = req.sourceUrl().replaceAll("/+$", "");
        var result = importService.startRemoteImport(sourceUrl, req.token());
        ctx.status(HttpStatus.CREATED).json(new ImportStartResponse(result.stationId(), result.stationName()));
    }

    @OpenApi(
            path = "/api/v1/admin/transfer/import/{stationId}/progress",
            methods = HttpMethod.GET,
            summary = "Get the progress of a running import",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "stationId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ImportProgressResponse.class)),
                @OpenApiResponse(status = "404")
            })
    private void importProgress(Context ctx) {
        int stationId = ctx.pathParamAsClass("stationId", Integer.class).get();
        var progress = importService.getProgress(stationId);
        if (progress == null) {
            throw new NotFoundResponse("No active import for station " + stationId);
        }
        ctx.json(new ImportProgressResponse(
                progress.stationId(),
                progress.stationName(),
                progress.status(),
                progress.totalTables(),
                progress.completedTables(),
                progress.currentTable(),
                progress.error()));
    }

    public record VersionResponse(String version) {}

    public record TokenResponse(String token, String version) {}

    public record TablesResponse(List<String> tables, String schemaHash, String appVersion) {}

    @OpenApiName("TransferImportRequest")
    public record ImportRequest(String sourceUrl, String token) {}

    public record ImportStartResponse(int stationId, String stationName) {}

    public record ImportProgressResponse(
            int stationId,
            String stationName,
            StationImportService.ImportProgress.Status status,
            int totalTables,
            int completedTables,
            String currentTable,
            String error) {}
}
