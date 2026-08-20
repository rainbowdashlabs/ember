/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.StorageWarningEvent;
import dev.chojo.ember.feature.storage.entity.StationStorageQuota;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Service for quota checking, delta tracking, and usage aggregation.
 * Resolves effective quotas from station overrides → instance defaults.
 */
@Singleton
public class StorageQuotaService {
    private static final Logger log = LoggerFactory.getLogger(StorageQuotaService.class);

    private final StorageUsageRepository usageRepository;
    private final StationStorageConfigRepository overrideRepository;
    private final Storage storageConfig;
    private final DomainEventBus eventBus;

    @Inject
    public StorageQuotaService(
            StorageUsageRepository usageRepository,
            StationStorageConfigRepository overrideRepository,
            Storage storageConfig,
            DomainEventBus eventBus) {
        this.usageRepository = usageRepository;
        this.overrideRepository = overrideRepository;
        this.storageConfig = storageConfig;
        this.eventBus = eventBus;
    }

    /**
     * Returns {@code true} when the station has its own remote-backend override. Instance-side
     * quotas (per-category, per-file, per-image, total) do not apply in that case - the station
     * is paying for its own storage and the instance has no business limiting it.
     */
    public boolean hasOwnBackend(int stationId) {
        return overrideRepository.findOne(stationId).isPresent();
    }

    /**
     * Checks whether an upload of the given size would exceed any quota.
     *
     * @throws StorageQuotaExceededException if the upload would exceed a quota
     */
    public void checkQuota(int stationId, StorageCategory category, long incomingBytes) {
        if (!category.enforcesQuota()) return;
        if (hasOwnBackend(stationId)) return;

        var quota = resolveQuotas(stationId);
        long categoryUsed = usageRepository.categoryBytes(stationId, category);
        long categoryLimit = categoryQuota(quota, category);
        if (categoryUsed + incomingBytes > categoryLimit) {
            throw new StorageQuotaExceededException(
                    category,
                    categoryUsed,
                    categoryLimit,
                    usageRepository.totalEnforcedBytes(stationId),
                    effectiveTotalQuota(quota));
        }

        long totalUsed = usageRepository.totalEnforcedBytes(stationId);
        long totalLimit = effectiveTotalQuota(quota);
        if (totalUsed + incomingBytes > totalLimit) {
            throw new StorageQuotaExceededException(category, categoryUsed, categoryLimit, totalUsed, totalLimit);
        }
    }

    /**
     * Checks whether a single file exceeds the per-file size limit.
     *
     * @throws StorageQuotaExceededException if the file exceeds the limit
     */
    public void checkFileSize(int stationId, long fileBytes) {
        if (hasOwnBackend(stationId)) return;
        var quota = resolveQuotas(stationId);
        long limit = quota.perFileBytes() != null ? quota.perFileBytes() : storageConfig.defaultPerFileBytes();
        if (fileBytes > limit) {
            throw new StorageQuotaExceededException(
                    "File size %d exceeds per-file limit %d".formatted(fileBytes, limit));
        }
    }

    /**
     * Checks whether a single image exceeds the per-image size limit.
     *
     * @throws StorageQuotaExceededException if the image exceeds the limit
     */
    public void checkImageSize(int stationId, long imageBytes) {
        if (hasOwnBackend(stationId)) return;
        var quota = resolveQuotas(stationId);
        long limit = quota.perImageBytes() != null ? quota.perImageBytes() : storageConfig.defaultPerImageBytes();
        if (imageBytes > limit) {
            throw new StorageQuotaExceededException(
                    "Image size %d exceeds per-image limit %d".formatted(imageBytes, limit));
        }
    }

    /**
     * Records a delta change in storage usage and checks warning thresholds.
     */
    public void trackDelta(int stationId, StorageCategory category, long bytesDelta, int fileCountDelta) {
        usageRepository.applyDelta(stationId, category, bytesDelta, fileCountDelta);
        checkWarningThreshold(stationId);
    }

    /**
     * Records a file upload: checks quota, then tracks the delta.
     */
    public void onFileUploaded(int stationId, StorageCategory category, long fileBytes) {
        checkQuota(stationId, category, fileBytes);
        trackDelta(stationId, category, fileBytes, 1);
    }

    /**
     * Records a file deletion.
     */
    public void onFileDeleted(int stationId, StorageCategory category, long fileBytes) {
        trackDelta(stationId, category, -fileBytes, -1);
    }

    /**
     * Returns all usage records for a station.
     */
    public List<StorageUsage> getUsage(int stationId) {
        return usageRepository.findByStation(stationId);
    }

    /**
     * Returns the total enforced bytes for a station.
     */
    public long getTotalUsedBytes(int stationId) {
        return usageRepository.totalEnforcedBytes(stationId);
    }

    /**
     * Resolves the effective total quota for a station.
     */
    public long getEffectiveTotalQuota(int stationId) {
        return effectiveTotalQuota(resolveQuotas(stationId));
    }

    /**
     * Returns the effective category quota for a station.
     */
    public long getEffectiveCategoryQuota(int stationId, StorageCategory category) {
        return categoryQuota(resolveQuotas(stationId), category);
    }

    /**
     * Updates a station's individual quota overrides.
     */
    public void updateStationQuotas(
            int stationId,
            Long totalBytes,
            Long kbBytes,
            Long boardBytes,
            Long imagesBytes,
            Long pagesBytes,
            Long perFileBytes,
            Long perImageBytes) {
        query("""
                UPDATE station SET
                    storage_quota_bytes = :total,
                    storage_quota_kb_bytes = :kb,
                    storage_quota_board_bytes = :board,
                    storage_quota_images_bytes = :images,
                    storage_quota_pages_bytes = :pages,
                    storage_per_file_bytes = :per_file,
                    storage_per_image_bytes = :per_image,
                    storage_preset_id = NULL
                WHERE id = :id;
                """)
                .single(call().bind("id", stationId)
                        .bind("total", totalBytes)
                        .bind("kb", kbBytes)
                        .bind("board", boardBytes)
                        .bind("images", imagesBytes)
                        .bind("pages", pagesBytes)
                        .bind("per_file", perFileBytes)
                        .bind("per_image", perImageBytes))
                .update();
        log.info("Updated storage quota overrides for station {}", stationId);
    }

    /**
     * Resolves per-station quota overrides from the station table.
     */
    StationStorageQuota resolveQuotas(int stationId) {
        return query("""
                SELECT id, storage_quota_bytes, storage_quota_kb_bytes, storage_quota_board_bytes,
                       storage_quota_images_bytes, storage_quota_pages_bytes, storage_per_file_bytes,
                       storage_per_image_bytes
                FROM station WHERE id = :id;
                """)
                .single(call().bind("id", stationId))
                .map(StationStorageQuota.map())
                .first()
                .orElse(new StationStorageQuota(stationId, null, null, null, null, null, null, null));
    }

    private long effectiveTotalQuota(StationStorageQuota quota) {
        return quota.quotaBytes() != null ? quota.quotaBytes() : storageConfig.defaultTotalBytes();
    }

    private long categoryQuota(StationStorageQuota quota, StorageCategory category) {
        return switch (category) {
            case KB_FILES -> quota.quotaKbBytes() != null ? quota.quotaKbBytes() : storageConfig.defaultKbBytes();
            case BOARD_ATTACHMENTS ->
                quota.quotaBoardBytes() != null ? quota.quotaBoardBytes() : storageConfig.defaultBoardBytes();
            case IMAGE_LOST_AND_FOUND, IMAGE_QUIZ_QUESTION, IMAGE_KB_ICON, IMAGE_KB_IMAGE, IMAGE_LOGO_FRAGMENT ->
                quota.quotaImagesBytes() != null ? quota.quotaImagesBytes() : storageConfig.defaultImagesBytes();
            case PAGE_FILES, PAGE_IMAGES ->
                quota.quotaPagesBytes() != null ? quota.quotaPagesBytes() : storageConfig.defaultPagesBytes();
            case MEMBER_DOCUMENTS ->
                quota.quotaKbBytes() != null ? quota.quotaKbBytes() : storageConfig.defaultKbBytes();
            case IMAGE_AVATAR, IMAGE_STATION_LOGO, DOCUMENT, DISCOVERY_KEY, MAP_TILE_CACHE, DEMO_AVATAR ->
                Long.MAX_VALUE;
        };
    }

    private void checkWarningThreshold(int stationId) {
        if (hasOwnBackend(stationId)) {
            if (isWarningSent(stationId)) setWarningSent(stationId, false);
            return;
        }
        var quota = resolveQuotas(stationId);
        long totalUsed = usageRepository.totalEnforcedBytes(stationId);
        long totalLimit = effectiveTotalQuota(quota);
        int percent = totalLimit > 0 ? (int) (totalUsed * 100 / totalLimit) : 0;

        boolean warningSent = isWarningSent(stationId);
        if (percent >= storageConfig.warningThresholdPercent() && !warningSent) {
            setWarningSent(stationId, true);
            log.info("Storage usage warning threshold reached for station {} at {}%", stationId, percent);
            eventBus.publish(new StorageWarningEvent(stationId, percent, totalUsed, totalLimit));
        } else if (percent < storageConfig.warningThresholdPercent() && warningSent) {
            setWarningSent(stationId, false);
        }
    }

    private boolean isWarningSent(int stationId) {
        return query("SELECT storage_warning_sent FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.getBoolean("storage_warning_sent"))
                .first()
                .orElse(false);
    }

    private void setWarningSent(int stationId, boolean sent) {
        query("UPDATE station SET storage_warning_sent = :sent WHERE id = :id;")
                .single(call().bind("sent", sent).bind("id", stationId))
                .update();
    }

    /**
     * Exception thrown when a storage quota is exceeded.
     */
    public static class StorageQuotaExceededException extends RuntimeException {
        private final StorageCategory category;
        private final long categoryUsed;
        private final long categoryQuota;
        private final long totalUsed;
        private final long totalQuota;

        public StorageQuotaExceededException(
                StorageCategory category, long categoryUsed, long categoryQuota, long totalUsed, long totalQuota) {
            super("Storage quota exceeded for category " + category);
            this.category = category;
            this.categoryUsed = categoryUsed;
            this.categoryQuota = categoryQuota;
            this.totalUsed = totalUsed;
            this.totalQuota = totalQuota;
        }

        public StorageQuotaExceededException(String message) {
            super(message);
            this.category = null;
            this.categoryUsed = 0;
            this.categoryQuota = 0;
            this.totalUsed = 0;
            this.totalQuota = 0;
        }

        public StorageCategory category() {
            return category;
        }

        public long categoryUsed() {
            return categoryUsed;
        }

        public long categoryQuota() {
            return categoryQuota;
        }

        public long totalUsed() {
            return totalUsed;
        }

        public long totalQuota() {
            return totalQuota;
        }
    }
}
