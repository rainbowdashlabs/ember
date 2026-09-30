/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceBackendRequest;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrateRequest;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.ApplyPresetRequest;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.PresetRequest;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.QuotaUpdateRequest;
import dev.chojo.ember.feature.storage.service.StorageUsageReportService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Storage usage for a station's managers, and the administrator's side of storage: usage of every
 * station, quotas and their presets, the history of storage changes, and the storage the instance keeps
 * its own files on.
 */
@Singleton
public class StorageRoutes implements Routes {
    private final StorageUsageReportService usageReport;
    private final StorageQuotaAdminService quotaAdmin;
    private final InstanceStorageSettingsService instanceStorage;
    private final StorageAuditLogService auditLog;

    @Inject
    public StorageRoutes(
            StorageUsageReportService usageReport,
            StorageQuotaAdminService quotaAdmin,
            InstanceStorageSettingsService instanceStorage,
            StorageAuditLogService auditLog) {
        this.usageReport = usageReport;
        this.quotaAdmin = quotaAdmin;
        this.instanceStorage = instanceStorage;
        this.auditLog = auditLog;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/storage/usage", this::getStationUsage, StationPermission.STATION_MANAGER);
        routes.get(prefix + "/admin/storage/usage", this::getAdminUsage, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + "/admin/storage/recalculate", this::recalculateAll, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/recalculate/{stationUid}",
                this::recalculateStation,
                InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/storage/presets", this::listPresets, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/presets",
                this::createPreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.put(
                prefix + "/admin/storage/presets/{id}",
                this::updatePreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.delete(
                prefix + "/admin/storage/presets/{id}",
                this::deletePreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/storage/presets/{id}/apply",
                this::applyPreset,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/storage/backend", this::getInstanceBackend, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/backend/probe", this::probeInstanceBackend, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/backend/probe-config",
                this::probeInstanceBackendConfig,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/backend/apply",
                this::applyInstanceBackend,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(
                prefix + "/admin/storage/backend/apply/status",
                this::migrateInstanceStatus,
                InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/storage/audit", this::listAudit, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/storage/stations/{stationUid}/quotas",
                this::updateStationQuotas,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.delete(
                prefix + "/admin/storage/stations/{stationUid}/quotas",
                this::resetStationQuotas,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
    }

    private void getStationUsage(Context ctx) {
        ctx.json(usageReport.stationUsage(UserSession.from(ctx).stationId()));
    }

    private void getAdminUsage(Context ctx) {
        ctx.json(usageReport.adminUsage());
    }

    private void recalculateAll(Context ctx) {
        quotaAdmin.recalculateAll();
        ctx.status(HttpStatus.ACCEPTED);
    }

    private void recalculateStation(Context ctx) {
        quotaAdmin.recalculateStation(pathUuid(ctx, "stationUid"));
        ctx.status(HttpStatus.OK);
    }

    private void listPresets(Context ctx) {
        ctx.json(quotaAdmin.presets());
    }

    private void createPreset(Context ctx) {
        ctx.json(quotaAdmin.createPreset(ctx.bodyAsClass(PresetRequest.class)));
    }

    private void updatePreset(Context ctx) {
        int id = pathInt(ctx, "id");
        ctx.json(quotaAdmin.updatePreset(id, ctx.bodyAsClass(PresetRequest.class)));
    }

    private void deletePreset(Context ctx) {
        quotaAdmin.deletePreset(pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void applyPreset(Context ctx) {
        int id = pathInt(ctx, "id");
        quotaAdmin.applyPreset(id, ctx.bodyAsClass(ApplyPresetRequest.class).stationUids());
        ctx.status(HttpStatus.OK);
    }

    private void getInstanceBackend(Context ctx) {
        ctx.json(instanceStorage.summary());
    }

    private void probeInstanceBackend(Context ctx) {
        ctx.json(instanceStorage.probe());
    }

    /**
     * Tests storage the operator has typed in but not saved, so the credentials can be checked before
     * the files are moved onto it.
     */
    private void probeInstanceBackendConfig(Context ctx) {
        ctx.json(instanceStorage.probe(ctx.bodyAsClass(InstanceBackendRequest.class)));
    }

    /**
     * Saves the storage of the instance and carries the files onto it, in one step, so the files and
     * the setting never point at different places.
     */
    private void applyInstanceBackend(Context ctx) {
        var request = ctx.bodyAsClass(InstanceMigrateRequest.class);
        ctx.json(instanceStorage.apply(actor(ctx), request));
    }

    private void migrateInstanceStatus(Context ctx) {
        ctx.json(instanceStorage.status());
    }

    private void listAudit(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(StorageAuditLogService.DEFAULT_LIMIT);
        ctx.json(auditLog.list(ctx.queryParam("before"), ctx.queryParam("stationUid"), limit));
    }

    private void updateStationQuotas(Context ctx) {
        var stationUid = pathUuid(ctx, "stationUid");
        quotaAdmin.updateStationQuotas(stationUid, ctx.bodyAsClass(QuotaUpdateRequest.class));
        ctx.status(HttpStatus.OK);
    }

    private void resetStationQuotas(Context ctx) {
        quotaAdmin.resetStationQuotas(pathUuid(ctx, "stationUid"));
        ctx.status(HttpStatus.OK);
    }

    private StorageBackendAuditService.Actor actor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session == null || session.account() == null) {
            throw Refusal.NO_ACCOUNT_BEHIND_INSTANCE_STORAGE_CHANGE.raise();
        }
        Integer memberId = session.member() != null ? session.member().id() : null;
        return StorageBackendAuditService.Actor.human(session.account().id(), memberId);
    }
}
