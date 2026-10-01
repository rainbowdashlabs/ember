/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageQuotaPreset;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository;
import dev.chojo.ember.lifecycle.TaskScheduler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * What an administrator decides about the storage stations may use: the presets, which station gets
 * which, a station's own limits, and counting a station's usage again.
 *
 * <p>A station named that is not here is refused rather than passed over, except when a preset is handed
 * to many at once: one station gone since the list was loaded does not keep the rest from getting it.
 */
@Singleton
public class StorageQuotaAdminService {
    private final StorageQuotaPresetRepository presetRepository;
    private final StorageQuotaService quotaService;
    private final StorageReconciliationService reconciliationService;
    private final StationRepository stationRepository;
    private final TaskScheduler scheduler;

    @Inject
    public StorageQuotaAdminService(
            StorageQuotaPresetRepository presetRepository,
            StorageQuotaService quotaService,
            StorageReconciliationService reconciliationService,
            StationRepository stationRepository,
            TaskScheduler scheduler) {
        this.presetRepository = presetRepository;
        this.quotaService = quotaService;
        this.reconciliationService = reconciliationService;
        this.stationRepository = stationRepository;
        this.scheduler = scheduler;
    }

    public List<StorageQuotaPreset> presets() {
        return presetRepository.findAll();
    }

    public StorageQuotaPreset createPreset(PresetRequest request) {
        return presetRepository.create(
                request.name(),
                request.total(),
                request.kb(),
                request.board(),
                request.images(),
                request.pages(),
                request.perFile(),
                request.perImage());
    }

    public StorageQuotaPreset updatePreset(int id, PresetRequest request) {
        return presetRepository.update(
                id,
                request.name(),
                request.total(),
                request.kb(),
                request.board(),
                request.images(),
                request.pages(),
                request.perFile(),
                request.perImage());
    }

    public void deletePreset(int id) {
        presetRepository.delete(id);
    }

    /**
     * Gives a preset's limits to every station named that is still here.
     *
     * @param id          the preset
     * @param stationUids the stations, by their identity
     */
    public void applyPreset(int id, List<String> stationUids) {
        List<UUID> uids =
                stationUids.stream().map(StorageQuotaAdminService::parseUid).toList();
        for (UUID uid : uids) {
            stationRepository.resolveId(uid).ifPresent(stationId -> presetRepository.applyToStation(id, stationId));
        }
    }

    /**
     * Sets a station's own limits, each of them left to its default where none is given.
     *
     * @param stationUid the station
     * @param request    the limits in bytes
     */
    public void updateStationQuotas(UUID stationUid, QuotaUpdateRequest request) {
        quotaService.updateStationQuotas(
                stationIdOf(stationUid),
                request.totalBytes(),
                request.kbBytes(),
                request.boardBytes(),
                request.imagesBytes(),
                request.pagesBytes(),
                request.perFileBytes(),
                request.perImageBytes());
    }

    public void resetStationQuotas(UUID stationUid) {
        presetRepository.resetStationQuotas(stationIdOf(stationUid));
    }

    /** Counts every station's usage again, in the background, since that walks every stored file. */
    public void recalculateAll() {
        scheduler.background("admin-reconcile-all", reconciliationService::reconcileAll);
    }

    public void recalculateStation(UUID stationUid) {
        reconciliationService.reconcileStation(stationIdOf(stationUid));
    }

    private int stationIdOf(UUID stationUid) {
        return stationRepository
                .resolveId(stationUid)
                .orElseThrow(StorageRefusal.STATION_NOT_HERE_FOR_STORAGE_ADMIN::raise);
    }

    /**
     * The station identity an administrator named, refused when it does not read as one.
     */
    static UUID parseUid(String raw) {
        if (raw == null) throw StorageRefusal.STATION_NOT_AN_IDENTITY_FOR_STORAGE_ADMIN.raise();
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw StorageRefusal.STATION_NOT_AN_IDENTITY_FOR_STORAGE_ADMIN.raise();
        }
    }

    public record PresetRequest(
            String name, long total, long kb, long board, long images, long pages, long perFile, long perImage) {}

    public record ApplyPresetRequest(List<String> stationUids) {}

    public record QuotaUpdateRequest(
            @Nullable Long totalBytes,
            @Nullable Long kbBytes,
            @Nullable Long boardBytes,
            @Nullable Long imagesBytes,
            @Nullable Long pagesBytes,
            @Nullable Long perFileBytes,
            @Nullable Long perImageBytes) {}
}
