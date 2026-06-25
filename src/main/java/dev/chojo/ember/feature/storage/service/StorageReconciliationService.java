/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Periodic sanity check that reconciles {@code station_storage_usage} against the actual bytes
 * on the backend. Hot-path tracking happens in {@link StorageQuotaService}; this service only
 * runs on a schedule (default daily) and on manual admin trigger to catch drift between the
 * incremental counters and the real on-disk size.
 *
 * <p>Reconciliation goes through {@link StorageService#sumSize(StorageScope, StorageCategory)},
 * which delegates to the resolved backend's prefix-sum primitive. The producer never walks the
 * filesystem directly — that detail stays behind the storage interface so S3 / SMB / SFTP
 * backends pick the appropriate native operation.
 */
@Singleton
public class StorageReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(StorageReconciliationService.class);

    private static final List<StorageCategory> STATION_CATEGORIES = List.of(
            StorageCategory.PAGE_FILES,
            StorageCategory.KB_FILES,
            StorageCategory.BOARD_ATTACHMENTS,
            StorageCategory.IMAGE_LOST_AND_FOUND,
            StorageCategory.IMAGE_QUIZ_QUESTION,
            StorageCategory.IMAGE_KB_ICON,
            StorageCategory.IMAGE_KB_IMAGE);

    private final StorageUsageRepository usageRepository;
    private final StationRepository stationRepository;
    private final StorageService storage;

    @Inject
    public StorageReconciliationService(
            StorageUsageRepository usageRepository,
            StationRepository stationRepository,
            StorageService storage,
            Storage storageConfig) {
        this.usageRepository = usageRepository;
        this.stationRepository = stationRepository;
        this.storage = storage;

        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "storage-reconciliation");
            t.setDaemon(true);
            return t;
        });
        int intervalHours = storageConfig.reconciliationIntervalHours();
        scheduler.scheduleWithFixedDelay(this::reconcileAll, 1, intervalHours * 60L, TimeUnit.MINUTES);
    }

    /**
     * Reconciles storage usage for every station.
     */
    public void reconcileAll() {
        try {
            log.info("Starting storage reconciliation for all stations");
            var stations = stationRepository.findAll();
            for (var station : stations) {
                reconcileStation(station.id());
            }
            log.info("Storage reconciliation completed for {} stations", stations.size());
        } catch (Exception e) {
            log.error("Error during storage reconciliation", e);
        }
    }

    /**
     * Reconciles storage usage for one station.
     */
    public void reconcileStation(int stationId) {
        try {
            UUID stationUid = stationRepository.resolveUid(stationId);
            if (stationUid == null) {
                log.warn("Skipping reconciliation for station {} — no UUID resolved", stationId);
                return;
            }
            var scope = new StorageScope.Station(stationId, stationUid);
            for (StorageCategory category : STATION_CATEGORIES) {
                reconcileCategory(stationId, scope, category);
            }
            reconcileAvatars(stationId);
        } catch (Exception e) {
            log.error("Error reconciling storage for station {}", stationId, e);
        }
    }

    private void reconcileCategory(int stationId, StorageScope.Station scope, StorageCategory category) {
        long totalBytes = storage.sumSize(scope, category);
        int fileCount = storage.listKeys(scope, category, "").size();
        usageRepository.setUsage(stationId, category, totalBytes, fileCount);
    }

    /**
     * Avatars live in an account-scoped tree but the per-station usage row needs the bytes
     * attributed to every station the account is a member of. Sum each member's account-scope
     * avatar bytes and roll the result up into the station's IMAGE_AVATAR row.
     */
    private void reconcileAvatars(int stationId) {
        var accountUids = query("""
                SELECT DISTINCT a.uid FROM ember_schema.account a
                JOIN ember_schema.station_member sm ON sm.account_id = a.id
                WHERE sm.station_id = :station_id AND a.uid IS NOT NULL;
                """)
                .single(call().bind("station_id", stationId))
                .map(row -> row.getString("uid"))
                .all();

        long totalBytes = 0;
        int fileCount = 0;
        for (String uidStr : accountUids) {
            UUID accountUid;
            try {
                accountUid = UUID.fromString(uidStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            var scope = new StorageScope.Account(accountUid);
            long bytes = storage.sumSize(scope, StorageCategory.IMAGE_AVATAR);
            if (bytes == 0) continue;
            totalBytes += bytes;
            fileCount +=
                    storage.listKeys(scope, StorageCategory.IMAGE_AVATAR, "").size();
        }
        usageRepository.setUsage(stationId, StorageCategory.IMAGE_AVATAR, totalBytes, fileCount);
    }
}
