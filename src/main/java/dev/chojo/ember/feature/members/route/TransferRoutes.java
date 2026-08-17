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
import dev.chojo.ember.feature.federation.service.FederationPartnerTransferFixupService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.feature.station.transfer.ImportProgress;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Routes for station data transfer including token-authenticated public export endpoints
 * for paginated table data and admin import/progress endpoints.
 */
@Singleton
public class TransferRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(TransferRoutes.class);

    private final StationExportService exportService;
    private final StationImportService importService;
    private final StationRepository stationRepository;
    private final FederationPartnerTransferFixupService federationFixup;

    @Inject
    public TransferRoutes(
            StationExportService exportService,
            StationImportService importService,
            StationRepository stationRepository,
            FederationPartnerTransferFixupService federationFixup) {
        this.exportService = exportService;
        this.importService = importService;
        this.stationRepository = stationRepository;
        this.federationFixup = federationFixup;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/version", this::getVersion);
        routes.post(
                prefix + "/station/transfer/create-token", this::createToken, StationPermission.STATION_IMPORT_EXPORT);
        routes.post(prefix + "/station/transfer/abort", this::abortTransfer, StationPermission.STATION_IMPORT_EXPORT);
        routes.get(prefix + "/station/transfer/status", this::transferStatus, StationPermission.LOGIN);

        // Token-authenticated export (public, for remote import)
        routes.get(prefix + "/public/transfer/{token}/tables", this::tokenListTables);
        routes.get(prefix + "/public/transfer/{token}/{table}", this::tokenExportTable);
        routes.post(prefix + "/public/transfer/{token}/abort", this::tokenAbortTransfer);
        routes.post(prefix + "/public/transfer/{token}/complete", this::tokenCompleteTransfer);

        // Import (async, fetches from remote)
        routes.post(prefix + "/admin/transfer/import", this::startImport, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/transfer/import/{stationUid}/progress",
                this::importProgress,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/transfer/import/{stationUid}/retry",
                this::retryImport,
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
        log.info("create token for station {}", session.stationId());
        String token = exportService.createTransferToken(session.stationId());
        ctx.json(new TokenResponse(token, exportService.getAppVersion()));
    }

    @OpenApi(
            path = "/api/v1/station/transfer/abort",
            methods = HttpMethod.POST,
            summary = "Abort an in-flight transfer and clear the station's read-only flag",
            tags = {"Transfer"},
            responses = @OpenApiResponse(status = "204"))
    private void abortTransfer(Context ctx) {
        UserSession session = UserSession.from(ctx);
        log.info("abort transfer for station {}", session.stationId());
        exportService.abortTransfer(session.stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station/transfer/status",
            methods = HttpMethod.GET,
            summary = "Returns whether the current station is being transferred out, and where to",
            tags = {"Transfer"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TransferStatusResponse.class)))
    private void transferStatus(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stationId = session.stationId();
        boolean readOnly = stationRepository.isReadOnlyForTransfer(stationId);
        String target = readOnly ? exportService.findTransferTarget(stationId).orElse(null) : null;
        ctx.json(new TransferStatusResponse(readOnly, target));
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
        int stationId =
                exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        String importingFrom = ctx.header("X-Ember-Importing-From");
        log.info(
                "tables manifest requested for station {} by destination {} - flipping read-only flag",
                stationId,
                importingFrom == null || importingFrom.isBlank() ? "<not declared>" : importingFrom);
        if (importingFrom != null && !importingFrom.isBlank()) {
            exportService.recordTransferTarget(token, importingFrom);
        }
        exportService.markTransferStarted(stationId);
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
        log.info("serving table '{}' offset={} limit={} for station {}", table, offset, limit, stationId);
        ctx.json(exportService.exportTable(stationId, table, offset, limit));
    }

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/abort",
            methods = HttpMethod.POST,
            summary = "Abort an in-flight transfer using a transfer token",
            description =
                    "Token-authenticated abort: the destination instance calls this endpoint when its import fails so the source can clear the read-only-for-transfer flag immediately instead of waiting for the idle-timeout watchdog. The endpoint is idempotent - a second call after the token has already been invalidated answers 403 like any expired token.",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "token", required = true),
            responses = {@OpenApiResponse(status = "204"), @OpenApiResponse(status = "403")})
    private void tokenAbortTransfer(Context ctx) {
        String token = ctx.pathParam("token");
        int stationId =
                exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        log.info("destination requested abort for station {}", stationId);
        exportService.abortTransfer(stationId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/public/transfer/{token}/complete",
            methods = HttpMethod.POST,
            summary = "Signal successful completion of a transfer using a transfer token",
            description =
                    "Token-authenticated completion: the destination instance calls this endpoint after every table imports successfully so the source can invalidate the token, point retained federation partner rows at the destination URL, and keep the source station read-only.",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "token", required = true),
            responses = {@OpenApiResponse(status = "204"), @OpenApiResponse(status = "403")})
    private void tokenCompleteTransfer(Context ctx) {
        String token = ctx.pathParam("token");
        int stationId =
                exportService.validateToken(token).orElseThrow(() -> new ForbiddenResponse("Invalid or expired token"));
        String header = ctx.header("X-Ember-Importing-From");
        String destinationUrl = header != null && !header.isBlank()
                ? header
                : exportService.findTransferTarget(stationId).orElse(null);
        log.info(
                "destination signalled completion for station {} (destination url={})",
                stationId,
                destinationUrl == null ? "<unknown>" : destinationUrl);
        exportService.markTransferComplete(stationId);
        final String url = destinationUrl;
        stationRepository
                .findById(stationId)
                .map(Station::uid)
                .ifPresent(uid -> federationFixup.flipSourceSideRetainedPartners(uid, url));
        ctx.status(HttpStatus.NO_CONTENT);
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
        if (req.token() == null || req.token().isBlank()) {
            throw new BadRequestResponse("token is required");
        }
        var parsed = StationExportService.parseToken(req.token())
                .orElseThrow(() -> new BadRequestResponse("Invalid transfer token"));
        String sourceUrl = (req.sourceUrl() != null && !req.sourceUrl().isBlank()) ? req.sourceUrl() : parsed.host();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new BadRequestResponse("token does not contain a source URL; sourceUrl is required");
        }
        sourceUrl = sourceUrl.replaceAll("/+$", "");
        var result = importService.startRemoteImport(sourceUrl, parsed.token());
        ctx.status(HttpStatus.CREATED).json(new ImportStartResponse(result.stationId(), result.stationName()));
    }

    @OpenApi(
            path = "/api/v1/admin/transfer/import/{stationUid}/progress",
            methods = HttpMethod.GET,
            summary = "Get the progress of a running import",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "stationUid", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ImportProgressResponse.class)),
                @OpenApiResponse(status = "404")
            })
    private void importProgress(Context ctx) {
        UUID stationUid = pathUuid(ctx, "stationUid");
        var progress = importService.getProgressByUid(stationUid);
        if (progress == null) {
            throw new NotFoundResponse("No active import for station " + stationUid);
        }
        ctx.json(new ImportProgressResponse(
                progress.stationId(),
                progress.stationName(),
                progress.status(),
                progress.phases(),
                progress.completedPhases(),
                progress.currentPhase(),
                progress.subTotal(),
                progress.subCompleted(),
                progress.error()));
    }

    @OpenApi(
            path = "/api/v1/admin/transfer/import/{stationUid}/retry",
            methods = HttpMethod.POST,
            summary = "Delete the half-imported station from a failed import and restart with the same token",
            tags = {"Transfer"},
            pathParams = @OpenApiParam(name = "stationUid", required = true),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ImportStartResponse.class)),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "404")
            })
    private void retryImport(Context ctx) {
        UUID stationUid = pathUuid(ctx, "stationUid");
        var result = importService.retryFailedImport(stationUid);
        ctx.status(HttpStatus.CREATED).json(new ImportStartResponse(result.stationId(), result.stationName()));
    }

    public record TransferStatusResponse(boolean readOnly, String targetInstanceUrl) {}

    public record VersionResponse(String version) {}

    public record TokenResponse(String token, String version) {}

    public record TablesResponse(List<String> tables, String schemaHash, String appVersion) {}

    @OpenApiName("TransferImportRequest")
    public record ImportRequest(String sourceUrl, String token) {}

    public record ImportStartResponse(int stationId, String stationName) {}

    public record ImportProgressResponse(
            int stationId,
            String stationName,
            ImportProgress.Status status,
            List<String> phases,
            int completedPhases,
            String currentPhase,
            int subTotal,
            int subCompleted,
            String error) {}
}
