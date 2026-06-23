/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageReconciliationService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class StorageRoutes implements Routes {
    private final StorageQuotaService quotaService;
    private final StorageUsageRepository usageRepository;
    private final StorageQuotaPresetRepository presetRepository;
    private final StationRepository stationRepository;
    private final StorageReconciliationService reconciliationService;
    private final StorageBackendResolver backendResolver;
    private final Storage storageConfig;

    @Inject
    public StorageRoutes(
            StorageQuotaService quotaService,
            StorageUsageRepository usageRepository,
            StorageQuotaPresetRepository presetRepository,
            StationRepository stationRepository,
            StorageReconciliationService reconciliationService,
            StorageBackendResolver backendResolver,
            Storage storageConfig) {
        this.quotaService = quotaService;
        this.usageRepository = usageRepository;
        this.presetRepository = presetRepository;
        this.stationRepository = stationRepository;
        this.reconciliationService = reconciliationService;
        this.backendResolver = backendResolver;
        this.storageConfig = storageConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Station-level usage (managers)
        routes.get(prefix + "/storage/usage", this::getStationUsage, StationPermission.STATION_MANAGER);

        // Admin: overview
        routes.get(prefix + "/admin/storage/usage", this::getAdminUsage, InstancePermission.ADMINISTRATOR);

        // Admin: reconciliation
        routes.post(prefix + "/admin/storage/recalculate", this::recalculateAll, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/recalculate/{stationUid}",
                this::recalculateStation,
                InstancePermission.ADMINISTRATOR);

        // Admin: quota presets CRUD
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

        // Admin: instance default storage backend
        routes.get(prefix + "/admin/storage/backend", this::getInstanceBackend, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/storage/backend/probe", this::probeInstanceBackend, InstancePermission.ADMINISTRATOR);

        // Admin: station quota management
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

    // -- Station usage --

    private void getStationUsage(Context ctx) {
        var session = UserSession.from(ctx);
        int stationId = session.stationId();
        var categories = usageRepository.findByStation(stationId);
        long totalBytes = categories.stream()
                .filter(u -> u.category().enforcesQuota())
                .mapToLong(StorageUsage::totalBytes)
                .sum();
        long quotaBytes = quotaService.getEffectiveTotalQuota(stationId);
        int quotaUsedPercent = quotaBytes > 0 ? (int) (totalBytes * 100 / quotaBytes) : 0;

        Map<String, Long> categoryQuotas = new HashMap<>();
        for (StorageCategory cat : StorageCategory.values()) {
            if (cat.enforcesQuota()) {
                categoryQuotas.put(cat.name(), quotaService.getEffectiveCategoryQuota(stationId, cat));
            }
        }

        ctx.json(new StationUsageResponse(
                categories.stream()
                        .map(u -> new CategoryUsage(u.category().name(), u.totalBytes(), u.fileCount()))
                        .toList(),
                totalBytes,
                quotaBytes,
                quotaUsedPercent,
                categoryQuotas));
    }

    // -- Admin overview --

    private void getAdminUsage(Context ctx) {
        var stations = stationRepository.findAll();
        var allUsage = usageRepository.findAll();
        var presetAssignments = presetRepository.findStationPresetAssignments();

        Map<Integer, List<StorageUsage>> usageByStation = new HashMap<>();
        for (var usage : allUsage) {
            usageByStation
                    .computeIfAbsent(usage.stationId(), k -> new ArrayList<>())
                    .add(usage);
        }

        var result = stations.stream()
                .map(station -> {
                    var stationUsages = usageByStation.getOrDefault(station.id(), List.of());
                    long totalBytes = stationUsages.stream()
                            .filter(u -> u.category().enforcesQuota())
                            .mapToLong(StorageUsage::totalBytes)
                            .sum();
                    long quotaBytes = quotaService.getEffectiveTotalQuota(station.id());
                    int quotaUsedPercent = quotaBytes > 0 ? (int) (totalBytes * 100 / quotaBytes) : 0;
                    var assignment = presetAssignments.get(station.id());
                    return new AdminStationUsage(
                            station.uid().toString(),
                            station.name(),
                            totalBytes,
                            quotaBytes,
                            quotaUsedPercent,
                            stationUsages.stream()
                                    .map(u -> new CategoryUsage(u.category().name(), u.totalBytes(), u.fileCount()))
                                    .toList(),
                            assignment != null ? assignment.presetId() : null,
                            assignment != null ? assignment.presetName() : null);
                })
                .toList();

        ctx.json(result);
    }

    // -- Reconciliation --

    private void recalculateAll(Context ctx) {
        Thread.ofVirtual().name("admin-reconcile-all").start(reconciliationService::reconcileAll);
        ctx.status(HttpStatus.ACCEPTED);
    }

    private void recalculateStation(Context ctx) {
        UUID uid = UUID.fromString(ctx.pathParam("stationUid"));
        var stationId = stationRepository.resolveId(uid);
        if (stationId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        reconciliationService.reconcileStation(stationId.get());
        ctx.status(HttpStatus.OK);
    }

    // -- Presets CRUD --

    private void listPresets(Context ctx) {
        ctx.json(presetRepository.findAll());
    }

    private void createPreset(Context ctx) {
        var req = ctx.bodyAsClass(PresetRequest.class);
        ctx.json(presetRepository.create(
                req.name(),
                req.total(),
                req.kb(),
                req.board(),
                req.images(),
                req.pages(),
                req.perFile(),
                req.perImage()));
    }

    private void updatePreset(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(PresetRequest.class);
        ctx.json(presetRepository.update(
                id,
                req.name(),
                req.total(),
                req.kb(),
                req.board(),
                req.images(),
                req.pages(),
                req.perFile(),
                req.perImage()));
    }

    private void deletePreset(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        presetRepository.delete(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void applyPreset(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(ApplyPresetRequest.class);
        for (String uidStr : req.stationUids()) {
            UUID uid = UUID.fromString(uidStr);
            stationRepository.resolveId(uid).ifPresent(stationId -> presetRepository.applyToStation(id, stationId));
        }
        ctx.status(HttpStatus.OK);
    }

    // -- Instance default backend --

    private void getInstanceBackend(Context ctx) {
        var backend = backendResolver.instanceDefault();
        var settings = storageConfig.backend();
        InstanceBackendSummary summary =
                switch (backend.type()) {
                    case LOCAL -> new LocalSummary(settings.local().root());
                    case SMB ->
                        new SmbSummary(
                                settings.smb().host(),
                                settings.smb().port(),
                                settings.smb().share(),
                                settings.smb().basePath(),
                                settings.smb().seal(),
                                settings.smb().dfs());
                    case SFTP ->
                        new SftpSummary(
                                settings.sftp().host(),
                                settings.sftp().port(),
                                settings.sftp().username(),
                                settings.sftp().basePath(),
                                !settings.sftp().knownHostsFingerprint().isBlank());
                    case S3 ->
                        new S3Summary(
                                settings.s3().endpoint(),
                                settings.s3().region(),
                                settings.s3().bucket(),
                                settings.s3().pathStyle(),
                                settings.s3().sseAlgorithm(),
                                settings.s3().basePath());
                };
        ctx.json(summary);
    }

    private void probeInstanceBackend(Context ctx) {
        HealthStatus status = backendResolver.instanceDefault().probe();
        ctx.json(new ProbeResult(
                status.healthy(),
                status.error().orElse(null),
                status.checkedAt().toString()));
    }

    // -- Station quota management --

    private void updateStationQuotas(Context ctx) {
        UUID uid = UUID.fromString(ctx.pathParam("stationUid"));
        var stationId = stationRepository.resolveId(uid);
        if (stationId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var req = ctx.bodyAsClass(QuotaUpdateRequest.class);
        quotaService.updateStationQuotas(
                stationId.get(),
                req.totalBytes(),
                req.kbBytes(),
                req.boardBytes(),
                req.imagesBytes(),
                req.pagesBytes(),
                req.perFileBytes(),
                req.perImageBytes());
        ctx.status(HttpStatus.OK);
    }

    private void resetStationQuotas(Context ctx) {
        UUID uid = UUID.fromString(ctx.pathParam("stationUid"));
        var stationId = stationRepository.resolveId(uid);
        if (stationId.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        presetRepository.resetStationQuotas(stationId.get());
        ctx.status(HttpStatus.OK);
    }

    // -- Request/Response records --

    record StationUsageResponse(
            List<CategoryUsage> categories,
            long totalBytes,
            long quotaBytes,
            int quotaUsedPercent,
            Map<String, Long> categoryQuotas) {}

    record CategoryUsage(String category, long totalBytes, int fileCount) {}

    record AdminStationUsage(
            String stationId,
            String stationName,
            long totalBytes,
            long quotaBytes,
            int quotaUsedPercent,
            List<CategoryUsage> categories,
            Integer presetId,
            String presetName) {}

    record PresetRequest(
            String name, long total, long kb, long board, long images, long pages, long perFile, long perImage) {}

    record ApplyPresetRequest(List<String> stationUids) {}

    record QuotaUpdateRequest(
            Long totalBytes,
            Long kbBytes,
            Long boardBytes,
            Long imagesBytes,
            Long pagesBytes,
            Long perFileBytes,
            Long perImageBytes) {}

    /**
     * Sealed sum type for the GET /admin/storage/backend response. Each variant carries the
     * concrete settings for its backend type; credentials are never included.
     */
    public sealed interface InstanceBackendSummary {
        StorageBackendType type();
    }

    public record LocalSummary(String root) implements InstanceBackendSummary {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.LOCAL;
        }
    }

    public record SmbSummary(String host, int port, String share, String basePath, boolean seal, boolean dfs)
            implements InstanceBackendSummary {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SMB;
        }
    }

    public record SftpSummary(String host, int port, String username, String basePath, boolean knownHostsPinned)
            implements InstanceBackendSummary {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.SFTP;
        }
    }

    public record S3Summary(
            String endpoint, String region, String bucket, boolean pathStyle, String sseAlgorithm, String basePath)
            implements InstanceBackendSummary {
        @Override
        public StorageBackendType type() {
            return StorageBackendType.S3;
        }
    }

    record ProbeResult(boolean healthy, String error, String checkedAt) {}
}
