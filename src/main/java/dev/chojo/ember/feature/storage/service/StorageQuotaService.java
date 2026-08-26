/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.StorageWarningEvent;
import dev.chojo.ember.feature.storage.entity.QuotaAuthority;
import dev.chojo.ember.feature.storage.entity.QuotaOrigin;
import dev.chojo.ember.feature.storage.entity.StationQuotas;
import dev.chojo.ember.feature.storage.entity.StationQuotas.ResolvedQuota;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
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
 *
 * <p>Resolves what a station may keep from what its cluster granted it, then what its cluster gives its
 * stations by default, then what an instance administrator set for it, then the instance configuration. A
 * station under a cluster is governed by that cluster: the instance's per-station override does not reach it,
 * because the instance's lever on a cluster is the pool it grants, and inside the pool the cluster decides.
 */
@Singleton
public class StorageQuotaService {
    private static final Logger log = LoggerFactory.getLogger(StorageQuotaService.class);

    private final StorageUsageRepository usageRepository;
    private final Storage storageConfig;
    private final DomainEventBus eventBus;

    @Inject
    public StorageQuotaService(StorageUsageRepository usageRepository, Storage storageConfig, DomainEventBus eventBus) {
        this.usageRepository = usageRepository;
        this.storageConfig = storageConfig;
        this.eventBus = eventBus;
    }

    /**
     * Whether nobody bounds what this station keeps, which is the case when whoever pays for its storage is
     * the station itself.
     *
     * @param stationId the station
     * @return {@code true} when no limit applies to it at all
     */
    public boolean isUnbounded(int stationId) {
        return resolveQuotas(stationId).total().origin() == QuotaOrigin.UNLIMITED;
    }

    /**
     * Checks whether an upload of the given size would exceed any quota.
     *
     * @throws StorageQuotaExceededException if the upload would exceed a quota
     */
    public void checkQuota(int stationId, StorageCategory category, long incomingBytes) {
        if (!category.enforcesQuota()) return;

        var quota = resolveQuotas(stationId);
        if (quota.authority() == QuotaAuthority.NOBODY) return;

        long categoryUsed = usageRepository.categoryBytes(stationId, category);
        long categoryLimit = categoryQuota(quota, category);
        if (categoryUsed + incomingBytes > categoryLimit) {
            log.info(
                    "Station {} is out of room for {}: {} of {} bytes used, {} more offered",
                    stationId,
                    category,
                    categoryUsed,
                    categoryLimit,
                    incomingBytes);
            throw new StorageQuotaExceededException(
                    category,
                    categoryUsed,
                    categoryLimit,
                    usageRepository.totalEnforcedBytes(stationId),
                    quota.total().bytes());
        }

        long totalUsed = usageRepository.totalEnforcedBytes(stationId);
        long totalLimit = quota.total().bytes();
        if (totalUsed + incomingBytes > totalLimit) {
            log.info(
                    "Station {} is out of room altogether: {} of {} bytes used, {} more offered",
                    stationId,
                    totalUsed,
                    totalLimit,
                    incomingBytes);
            throw new StorageQuotaExceededException(category, categoryUsed, categoryLimit, totalUsed, totalLimit);
        }
    }

    /**
     * Checks whether a single file exceeds the per-file size limit.
     *
     * @throws StorageQuotaExceededException if the file exceeds the limit
     */
    public void checkFileSize(int stationId, long fileBytes) {
        long limit = resolveQuotas(stationId).perFile().bytes();
        if (fileBytes > limit) {
            log.info("Station {} offered a {} byte file, over its {} byte limit", stationId, fileBytes, limit);
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
        long limit = resolveQuotas(stationId).perImage().bytes();
        if (imageBytes > limit) {
            log.info("Station {} offered a {} byte image, over its {} byte limit", stationId, imageBytes, limit);
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
        log.debug(
                "Storage of station {} moved by {} bytes and {} file(s) in {}",
                stationId,
                bytesDelta,
                fileCountDelta,
                category);
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
        return resolveQuotas(stationId).total().bytes();
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
     * What a station may keep, with every dimension resolved and carrying where its number came from.
     *
     * <p>One read for all of it: the station's own overrides, the grant its cluster made it, its cluster's
     * defaults, and whether either of them brought a storage backend of their own. Reading them together is
     * what lets one answer say both how much and on whose word.
     *
     * @param stationId the station
     * @return its quotas, or the instance's own defaults when there is no such station
     */
    public StationQuotas resolveQuotas(int stationId) {
        return query("""
                SELECT s.id,
                       c.id AS cluster_id,
                       s.storage_quota_bytes,
                       s.storage_quota_kb_bytes,
                       s.storage_quota_board_bytes,
                       s.storage_quota_images_bytes,
                       s.storage_quota_pages_bytes,
                       s.storage_per_file_bytes,
                       s.storage_per_image_bytes,
                       q.quota_bytes        AS granted_bytes,
                       q.quota_kb_bytes     AS granted_kb_bytes,
                       q.quota_board_bytes  AS granted_board_bytes,
                       q.quota_images_bytes AS granted_images_bytes,
                       q.quota_pages_bytes  AS granted_pages_bytes,
                       q.per_file_bytes     AS granted_per_file_bytes,
                       q.per_image_bytes    AS granted_per_image_bytes,
                       c.default_quota_bytes,
                       c.default_quota_kb_bytes,
                       c.default_quota_board_bytes,
                       c.default_quota_images_bytes,
                       c.default_quota_pages_bytes,
                       c.default_per_file_bytes,
                       c.default_per_image_bytes,
                       ssc.station_id IS NOT NULL AS station_backend,
                       css.station_id IS NOT NULL AS cluster_backend
                FROM station s
                    -- A cluster's own store is the home station it owns, which carries no membership row of
                    -- its own, so it is found the other way round
                    LEFT JOIN cluster c ON c.id = s.cluster_id OR c.home_station_id = s.id
                    LEFT JOIN cluster_station_quota q ON q.station_id = s.id
                    LEFT JOIN station_storage_config ssc ON ssc.station_id = s.id
                    -- Who pays follows where the bytes are and not what anybody decided: a station its
                    -- cluster has configured a backend for but never moved is still on the instance's disk
                    LEFT JOIN cluster_station_storage css ON css.station_id = s.id
                WHERE s.id = :id;
                """)
                .single(call().bind("id", stationId))
                .map(row -> {
                    QuotaAuthority authority = row.getBoolean("station_backend")
                            ? QuotaAuthority.NOBODY
                            : row.getBoolean("cluster_backend") ? QuotaAuthority.CLUSTER : QuotaAuthority.INSTANCE;
                    boolean underCluster = row.getObject("cluster_id", Integer.class) != null;
                    return new StationQuotas(
                            row.getInt("id"),
                            authority,
                            resolve(
                                    authority,
                                    underCluster,
                                    row.getObject("granted_bytes", Long.class),
                                    row.getObject("default_quota_bytes", Long.class),
                                    row.getObject("storage_quota_bytes", Long.class),
                                    storageConfig.defaultTotalBytes()),
                            resolve(
                                    authority,
                                    underCluster,
                                    row.getObject("granted_kb_bytes", Long.class),
                                    row.getObject("default_quota_kb_bytes", Long.class),
                                    row.getObject("storage_quota_kb_bytes", Long.class),
                                    storageConfig.defaultKbBytes()),
                            resolve(
                                    authority,
                                    underCluster,
                                    row.getObject("granted_board_bytes", Long.class),
                                    row.getObject("default_quota_board_bytes", Long.class),
                                    row.getObject("storage_quota_board_bytes", Long.class),
                                    storageConfig.defaultBoardBytes()),
                            resolve(
                                    authority,
                                    underCluster,
                                    row.getObject("granted_images_bytes", Long.class),
                                    row.getObject("default_quota_images_bytes", Long.class),
                                    row.getObject("storage_quota_images_bytes", Long.class),
                                    storageConfig.defaultImagesBytes()),
                            resolve(
                                    authority,
                                    underCluster,
                                    row.getObject("granted_pages_bytes", Long.class),
                                    row.getObject("default_quota_pages_bytes", Long.class),
                                    row.getObject("storage_quota_pages_bytes", Long.class),
                                    storageConfig.defaultPagesBytes()),
                            resolve(
                                    authority,
                                    underCluster,
                                    row.getObject("granted_per_file_bytes", Long.class),
                                    row.getObject("default_per_file_bytes", Long.class),
                                    row.getObject("storage_per_file_bytes", Long.class),
                                    storageConfig.defaultPerFileBytes()),
                            resolve(
                                    authority,
                                    underCluster,
                                    row.getObject("granted_per_image_bytes", Long.class),
                                    row.getObject("default_per_image_bytes", Long.class),
                                    row.getObject("storage_per_image_bytes", Long.class),
                                    storageConfig.defaultPerImageBytes()));
                })
                .first()
                .orElseGet(() -> instanceDefaults(stationId));
    }

    /**
     * One dimension, resolved down the chain.
     *
     * <p>The cluster's grant first, then what the cluster gives its stations by default. The instance's
     * per-station override comes next and is skipped for a station under a cluster, because the instance's
     * lever there is the pool it granted the cluster rather than a number on one of its stations. The
     * instance's configured default is the last word, unless nobody who could set one is paying.
     */
    private static ResolvedQuota resolve(
            QuotaAuthority authority,
            boolean underCluster,
            Long granted,
            Long clusterDefault,
            Long override,
            long instanceDefault) {
        if (authority == QuotaAuthority.NOBODY) return ResolvedQuota.unlimited();
        if (granted != null) return new ResolvedQuota(granted, QuotaOrigin.CLUSTER_GRANT);
        if (clusterDefault != null) return new ResolvedQuota(clusterDefault, QuotaOrigin.CLUSTER_DEFAULT);
        if (authority == QuotaAuthority.CLUSTER) return ResolvedQuota.unlimited();
        if (!underCluster && override != null) return new ResolvedQuota(override, QuotaOrigin.INSTANCE_OVERRIDE);
        return new ResolvedQuota(instanceDefault, QuotaOrigin.INSTANCE_DEFAULT);
    }

    /** What a station nobody has said anything about may keep, which is what the instance configuration says. */
    private StationQuotas instanceDefaults(int stationId) {
        return new StationQuotas(
                stationId,
                QuotaAuthority.INSTANCE,
                new ResolvedQuota(storageConfig.defaultTotalBytes(), QuotaOrigin.INSTANCE_DEFAULT),
                new ResolvedQuota(storageConfig.defaultKbBytes(), QuotaOrigin.INSTANCE_DEFAULT),
                new ResolvedQuota(storageConfig.defaultBoardBytes(), QuotaOrigin.INSTANCE_DEFAULT),
                new ResolvedQuota(storageConfig.defaultImagesBytes(), QuotaOrigin.INSTANCE_DEFAULT),
                new ResolvedQuota(storageConfig.defaultPagesBytes(), QuotaOrigin.INSTANCE_DEFAULT),
                new ResolvedQuota(storageConfig.defaultPerFileBytes(), QuotaOrigin.INSTANCE_DEFAULT),
                new ResolvedQuota(storageConfig.defaultPerImageBytes(), QuotaOrigin.INSTANCE_DEFAULT));
    }

    private long categoryQuota(StationQuotas quota, StorageCategory category) {
        return switch (category) {
            case KB_FILES -> quota.kb().bytes();
            case BOARD_ATTACHMENTS -> quota.board().bytes();
            case IMAGE_LOST_AND_FOUND, IMAGE_QUIZ_QUESTION, IMAGE_KB_ICON, IMAGE_KB_IMAGE, IMAGE_LOGO_FRAGMENT ->
                quota.images().bytes();
            case MEDIA_FILES, MEDIA_IMAGES -> quota.pages().bytes();
            case MEMBER_DOCUMENTS, MOVEMENT_DOCUMENTS -> quota.kb().bytes();
            // A quota limits what one station may keep. What the instance holds is not any
            // station's to be charged for, so nothing here has a limit to look up.
            case IMAGE_AVATAR,
                    IMAGE_STATION_LOGO,
                    DOCUMENT,
                    DISCOVERY_KEY,
                    MAP_TILE_CACHE,
                    DEMO_AVATAR,
                    INSTANCE_MEDIA_FILES -> Long.MAX_VALUE;
        };
    }

    private void checkWarningThreshold(int stationId) {
        var quota = resolveQuotas(stationId);
        if (quota.authority() == QuotaAuthority.NOBODY || quota.total().origin() == QuotaOrigin.UNLIMITED) {
            if (isWarningSent(stationId)) setWarningSent(stationId, false);
            return;
        }
        long totalUsed = usageRepository.totalEnforcedBytes(stationId);
        long totalLimit = quota.total().bytes();
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
