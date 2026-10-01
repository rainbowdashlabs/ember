/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.storage.service.StationStorageBackendService;
import dev.chojo.ember.feature.storage.service.StationStorageBackendService.BackendOverrideResponse;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService.AuditEntryResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.BackendOverrideRequest;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.MigrationResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
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

/**
 * Station-scoped self-service routes for picking a remote storage backend. A station manager
 * can override the inherited instance default for the entire station without involving an
 * instance admin. The override covers every station-scoped movable category at once.
 */
@Singleton
public class StationStorageBackendRoutes implements Routes {
    private final StationStorageBackendService backendService;
    private final StorageAuditLogService auditLog;

    @Inject
    public StationStorageBackendRoutes(StationStorageBackendService backendService, StorageAuditLogService auditLog) {
        this.backendService = backendService;
        this.auditLog = auditLog;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/storage/backend", this::get, StationPermission.STATION_ADMINISTRATOR);
        routes.post(prefix + "/station/storage/backend/probe", this::probe, StationPermission.STATION_ADMINISTRATOR);
        routes.post(
                prefix + "/station/storage/backend/probe-config",
                this::probeConfig,
                StationPermission.STATION_ADMINISTRATOR);
        routes.post(prefix + "/station/storage/backend/apply", this::apply, StationPermission.STATION_ADMINISTRATOR);
        routes.get(prefix + "/station/storage/audit", this::listAudit, StationPermission.STATION_ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/station/storage/backend",
            methods = HttpMethod.GET,
            summary = "What is behind the station's files and who decided it",
            tags = {"Storage"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = BackendOverrideResponse.class)))
    private void get(Context ctx) {
        ctx.json(backendService.describe(sessionStationId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/station/storage/backend/apply",
            methods = HttpMethod.POST,
            summary = "Move the station's files onto another storage",
            tags = {"Storage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BackendOverrideRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MigrationResponse.class)))
    private void apply(Context ctx) {
        Actor actor = actor(ctx);
        int stationId = sessionStationId(ctx);
        ctx.json(backendService.apply(actor, stationId, ctx.bodyAsClass(BackendOverrideRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/station/storage/backend/probe",
            methods = HttpMethod.POST,
            summary = "Check whether the station's own storage answers",
            tags = {"Storage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProbeResult.class)))
    private void probe(Context ctx) {
        Actor actor = actor(ctx);
        ctx.json(backendService.probe(actor, sessionStationId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/station/storage/backend/probe-config",
            methods = HttpMethod.POST,
            summary = "Check whether a storage not saved yet would answer",
            tags = {"Storage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BackendOverrideRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProbeResult.class)))
    private void probeConfig(Context ctx) {
        int stationId = sessionStationId(ctx);
        ctx.json(backendService.probe(stationId, ctx.bodyAsClass(BackendOverrideRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/station/storage/audit",
            methods = HttpMethod.GET,
            summary = "The history of the station's storage changes",
            tags = {"Storage"},
            queryParams = {
                @OpenApiParam(name = "before", type = String.class),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AuditEntryResponse[].class)))
    private void listAudit(Context ctx) {
        int stationId = sessionStationId(ctx);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(StorageAuditLogService.DEFAULT_LIMIT);
        ctx.json(auditLog.listForStation(stationId, ctx.queryParam("before"), limit));
    }

    private int sessionStationId(Context ctx) {
        return backendService.requireStation(StationSession.from(ctx).stationId());
    }

    private Actor actor(Context ctx) {
        StationSession session = StationSession.from(ctx);
        if (session.user().account() == null) throw Refusal.NO_ACCOUNT_BEHIND_STORAGE_CHANGE.raise();
        return Actor.human(session.accountId(), session.member().id());
    }
}
