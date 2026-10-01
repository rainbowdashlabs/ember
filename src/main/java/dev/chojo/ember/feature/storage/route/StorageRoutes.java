/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.storage.entity.StorageQuotaPreset;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceBackendRequest;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceBackendSummary;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrateRequest;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrationResultResponse;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrationStatusResponse;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService.AuditEntryResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.ApplyPresetRequest;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.PresetRequest;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.QuotaUpdateRequest;
import dev.chojo.ember.feature.storage.service.StorageUsageReportService;
import dev.chojo.ember.feature.storage.service.StorageUsageReportService.AdminStationUsage;
import dev.chojo.ember.feature.storage.service.StorageUsageReportService.StationUsageResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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

    @OpenApi(
            path = "/api/v1/storage/usage",
            methods = HttpMethod.GET,
            summary = "How much storage the caller's station uses",
            tags = {"Storage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationUsageResponse.class)))
    private void getStationUsage(Context ctx) {
        ctx.json(usageReport.stationUsage(StationSession.from(ctx).stationId()));
    }

    @OpenApi(
            path = "/api/v1/admin/storage/usage",
            methods = HttpMethod.GET,
            summary = "How much storage every station uses",
            tags = {"Storage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AdminStationUsage[].class)))
    private void getAdminUsage(Context ctx) {
        ctx.json(usageReport.adminUsage());
    }

    @OpenApi(
            path = "/api/v1/admin/storage/recalculate",
            methods = HttpMethod.POST,
            summary = "Count the storage of every station again",
            tags = {"Storage"},
            responses = @OpenApiResponse(status = "202"))
    private void recalculateAll(Context ctx) {
        quotaAdmin.recalculateAll();
        ctx.status(HttpStatus.ACCEPTED);
    }

    @OpenApi(
            path = "/api/v1/admin/storage/recalculate/{stationUid}",
            methods = HttpMethod.POST,
            summary = "Count the storage of one station again",
            tags = {"Storage"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void recalculateStation(Context ctx) {
        quotaAdmin.recalculateStation(pathUuid(ctx, "stationUid"));
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/admin/storage/presets",
            methods = HttpMethod.GET,
            summary = "List the quota presets",
            tags = {"Storage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StorageQuotaPreset[].class)))
    private void listPresets(Context ctx) {
        ctx.json(quotaAdmin.presets());
    }

    @OpenApi(
            path = "/api/v1/admin/storage/presets",
            methods = HttpMethod.POST,
            summary = "Create a quota preset",
            tags = {"Storage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PresetRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StorageQuotaPreset.class)))
    private void createPreset(Context ctx) {
        ctx.json(quotaAdmin.createPreset(ctx.bodyAsClass(PresetRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/storage/presets/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a quota preset",
            tags = {"Storage"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PresetRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StorageQuotaPreset.class)))
    private void updatePreset(Context ctx) {
        int id = pathInt(ctx, "id");
        ctx.json(quotaAdmin.updatePreset(id, ctx.bodyAsClass(PresetRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/storage/presets/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a quota preset",
            tags = {"Storage"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void deletePreset(Context ctx) {
        quotaAdmin.deletePreset(pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/storage/presets/{id}/apply",
            methods = HttpMethod.POST,
            summary = "Apply a quota preset to stations",
            tags = {"Storage"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ApplyPresetRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void applyPreset(Context ctx) {
        int id = pathInt(ctx, "id");
        quotaAdmin.applyPreset(id, ctx.bodyAsClass(ApplyPresetRequest.class).stationUids());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/admin/storage/backend",
            methods = HttpMethod.GET,
            summary = "The storage the instance keeps its own files on",
            tags = {"Storage"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = InstanceBackendSummary.class)))
    private void getInstanceBackend(Context ctx) {
        ctx.json(instanceStorage.summary());
    }

    @OpenApi(
            path = "/api/v1/admin/storage/backend/probe",
            methods = HttpMethod.POST,
            summary = "Check whether the instance's storage answers",
            tags = {"Storage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProbeResult.class)))
    private void probeInstanceBackend(Context ctx) {
        ctx.json(instanceStorage.probe());
    }

    /**
     * Tests storage the operator has typed in but not saved, so the credentials can be checked before
     * the files are moved onto it.
     */
    @OpenApi(
            path = "/api/v1/admin/storage/backend/probe-config",
            methods = HttpMethod.POST,
            summary = "Check whether a storage not saved yet would answer",
            tags = {"Storage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InstanceBackendRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProbeResult.class)))
    private void probeInstanceBackendConfig(Context ctx) {
        ctx.json(instanceStorage.probe(ctx.bodyAsClass(InstanceBackendRequest.class)));
    }

    /**
     * Saves the storage of the instance and carries the files onto it, in one step, so the files and
     * the setting never point at different places.
     */
    @OpenApi(
            path = "/api/v1/admin/storage/backend/apply",
            methods = HttpMethod.POST,
            summary = "Save the instance's storage and move its files onto it",
            tags = {"Storage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InstanceMigrateRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = InstanceMigrationResultResponse.class)))
    private void applyInstanceBackend(Context ctx) {
        var request = ctx.bodyAsClass(InstanceMigrateRequest.class);
        ctx.json(instanceStorage.apply(actor(ctx), request));
    }

    @OpenApi(
            path = "/api/v1/admin/storage/backend/apply/status",
            methods = HttpMethod.GET,
            summary = "Whether the instance's files are being moved right now",
            tags = {"Storage"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = InstanceMigrationStatusResponse.class)))
    private void migrateInstanceStatus(Context ctx) {
        ctx.json(instanceStorage.status());
    }

    @OpenApi(
            path = "/api/v1/admin/storage/audit",
            methods = HttpMethod.GET,
            summary = "The history of storage changes across the instance",
            tags = {"Storage"},
            queryParams = {
                @OpenApiParam(name = "before", type = String.class),
                @OpenApiParam(name = "stationUid", type = UUID.class),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AuditEntryResponse[].class)))
    private void listAudit(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(StorageAuditLogService.DEFAULT_LIMIT);
        ctx.json(auditLog.list(ctx.queryParam("before"), ctx.queryParam("stationUid"), limit));
    }

    @OpenApi(
            path = "/api/v1/admin/storage/stations/{stationUid}/quotas",
            methods = HttpMethod.PUT,
            summary = "Override one station's quotas",
            tags = {"Storage"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = QuotaUpdateRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void updateStationQuotas(Context ctx) {
        var stationUid = pathUuid(ctx, "stationUid");
        quotaAdmin.updateStationQuotas(stationUid, ctx.bodyAsClass(QuotaUpdateRequest.class));
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/admin/storage/stations/{stationUid}/quotas",
            methods = HttpMethod.DELETE,
            summary = "Return one station to the default quotas",
            tags = {"Storage"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            responses = @OpenApiResponse(status = "200"))
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
