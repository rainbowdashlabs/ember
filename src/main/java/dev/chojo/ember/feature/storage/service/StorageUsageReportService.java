/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.QuotaOrigin;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository;
import dev.chojo.ember.feature.storage.repository.StorageQuotaPresetRepository.StationPresetAssignment;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How much storage stations use against what they may use, for a station's own page and for the
 * administrator's overview of all of them.
 *
 * <p>A station standing on storage somebody else provides has no quota here: what it is shown is that
 * there is no limit, rather than a limit of nothing.
 */
@Singleton
public class StorageUsageReportService {
    private final StorageQuotaService quotaService;
    private final StorageUsageRepository usageRepository;
    private final StorageQuotaPresetRepository presetRepository;
    private final StationRepository stationRepository;

    @Inject
    public StorageUsageReportService(
            StorageQuotaService quotaService,
            StorageUsageRepository usageRepository,
            StorageQuotaPresetRepository presetRepository,
            StationRepository stationRepository) {
        this.quotaService = quotaService;
        this.usageRepository = usageRepository;
        this.presetRepository = presetRepository;
        this.stationRepository = stationRepository;
    }

    /**
     * What one station uses and may use, category by category.
     *
     * @param stationId the station
     * @return its usage
     */
    public StationUsageResponse stationUsage(int stationId) {
        var usages = usageRepository.findByStation(stationId);
        long totalBytes = enforcedBytes(usages);
        boolean usesOwnBackend = quotaService.isUnbounded(stationId);
        long quotaBytes = usesOwnBackend ? 0L : quotaService.getEffectiveTotalQuota(stationId);
        Map<String, Long> categoryQuotas = new HashMap<>();
        if (!usesOwnBackend) {
            for (StorageCategory category : StorageCategory.values()) {
                if (category.enforcesQuota()) {
                    categoryQuotas.put(category.name(), quotaService.getEffectiveCategoryQuota(stationId, category));
                }
            }
        }
        return new StationUsageResponse(
                categoriesOf(usages),
                totalBytes,
                quotaBytes,
                percentOf(totalBytes, quotaBytes),
                categoryQuotas,
                usesOwnBackend);
    }

    /**
     * What every station uses, with the quota it answers to and the preset it was given, if any.
     *
     * @return one row per station
     */
    public List<AdminStationUsage> adminUsage() {
        Map<Integer, List<StorageUsage>> usageByStation =
                usageRepository.findAll().stream().collect(Collectors.groupingBy(StorageUsage::stationId));
        var assignments = presetRepository.findStationPresetAssignments();
        return stationRepository.findAll().stream()
                .map(station -> adminUsageOf(
                        station, usageByStation.getOrDefault(station.id(), List.of()), assignments.get(station.id())))
                .toList();
    }

    private AdminStationUsage adminUsageOf(
            Station station, List<StorageUsage> usages, StationPresetAssignment assignment) {
        long totalBytes = enforcedBytes(usages);
        var total = quotaService.resolveQuotas(station.id()).total();
        boolean usesOwnBackend = total.origin() == QuotaOrigin.UNLIMITED;
        long quotaBytes = usesOwnBackend ? 0L : total.bytes();
        return new AdminStationUsage(
                station.uid().toString(),
                station.name(),
                totalBytes,
                quotaBytes,
                percentOf(totalBytes, quotaBytes),
                categoriesOf(usages),
                assignment != null ? assignment.presetId() : null,
                assignment != null ? assignment.presetName() : null,
                usesOwnBackend,
                total.origin());
    }

    private static long enforcedBytes(List<StorageUsage> usages) {
        return usages.stream()
                .filter(usage -> usage.category().enforcesQuota())
                .mapToLong(StorageUsage::totalBytes)
                .sum();
    }

    private static int percentOf(long usedBytes, long quotaBytes) {
        return quotaBytes > 0 ? (int) (usedBytes * 100 / quotaBytes) : 0;
    }

    private static List<CategoryUsage> categoriesOf(List<StorageUsage> usages) {
        return usages.stream()
                .map(usage -> new CategoryUsage(usage.category(), usage.totalBytes(), usage.fileCount()))
                .toList();
    }

    public record StationUsageResponse(
            List<CategoryUsage> categories,
            long totalBytes,
            long quotaBytes,
            int quotaUsedPercent,
            Map<String, Long> categoryQuotas,
            boolean usesOwnBackend) {}

    public record CategoryUsage(StorageCategory category, long totalBytes, int fileCount) {}

    /**
     * @param origin whose word the quota is on, so a station governed by a cluster reads as one rather than as
     *               a station whose override an administrator can usefully change
     */
    public record AdminStationUsage(
            String stationId,
            String stationName,
            long totalBytes,
            long quotaBytes,
            int quotaUsedPercent,
            List<CategoryUsage> categories,
            @Nullable Integer presetId,
            @Nullable String presetName,
            boolean usesOwnBackend,
            QuotaOrigin origin) {}
}
