/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.storage.service.StationStorageBackendService;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.BackendOverrideRequest;
import io.javalin.http.Context;
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

    private void get(Context ctx) {
        ctx.json(backendService.describe(sessionStationId(ctx)));
    }

    private void apply(Context ctx) {
        Actor actor = actor(ctx);
        int stationId = sessionStationId(ctx);
        ctx.json(backendService.apply(actor, stationId, ctx.bodyAsClass(BackendOverrideRequest.class)));
    }

    private void probe(Context ctx) {
        Actor actor = actor(ctx);
        ctx.json(backendService.probe(actor, sessionStationId(ctx)));
    }

    private void probeConfig(Context ctx) {
        int stationId = sessionStationId(ctx);
        ctx.json(backendService.probe(stationId, ctx.bodyAsClass(BackendOverrideRequest.class)));
    }

    private void listAudit(Context ctx) {
        int stationId = sessionStationId(ctx);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(StorageAuditLogService.DEFAULT_LIMIT);
        ctx.json(auditLog.listForStation(stationId, ctx.queryParam("before"), limit));
    }

    private int sessionStationId(Context ctx) {
        return backendService.requireStation(UserSession.from(ctx).stationId());
    }

    private Actor actor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.account() == null) throw Refusal.NO_ACCOUNT_BEHIND_STORAGE_CHANGE.raise();
        Integer memberId = session.member() != null ? session.member().id() : null;
        return Actor.human(session.account().id(), memberId);
    }
}
