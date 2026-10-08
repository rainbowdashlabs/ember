/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.transfer.ActiveImports;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Corrects the usage counters {@link StorageQuotaService} keeps incrementally against the bytes the
 * backends actually hold, on a schedule and when an administrator asks.
 */
@Singleton
public class StorageReconciliationService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(StorageReconciliationService.class);

    private static final List<StorageCategory> STATION_CATEGORIES = List.of(
            StorageCategory.MEDIA_FILES,
            StorageCategory.KB_FILES,
            StorageCategory.BOARD_ATTACHMENTS,
            StorageCategory.IMAGE_LOST_AND_FOUND,
            StorageCategory.IMAGE_QUIZ_QUESTION,
            StorageCategory.IMAGE_KB_ICON,
            StorageCategory.IMAGE_KB_IMAGE,
            StorageCategory.IMAGE_KB_FILE_PICTURE);

    private final StorageUsageRepository usageRepository;
    private final StationRepository stationRepository;
    private final StorageService storage;
    private final StationStorageConfigRepository configRepository;
    private final ActiveImports activeImports;
    private final Duration reconciliationInterval;

    @Inject
    public StorageReconciliationService(
            StorageUsageRepository usageRepository,
            StationRepository stationRepository,
            StorageService storage,
            StationStorageConfigRepository configRepository,
            ActiveImports activeImports,
            Storage storageConfig) {
        this.usageRepository = usageRepository;
        this.stationRepository = stationRepository;
        this.storage = storage;
        this.configRepository = configRepository;
        this.activeImports = activeImports;
        this.reconciliationInterval = Duration.ofHours(storageConfig.reconciliationIntervalHours());
    }

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
     * Corrects one station's usage counters and removes the files no row of it names.
     *
     * <p>A station a transfer holds is left alone: one that moved away, one being handed over while
     * its transfer token is open, and one an import is still writing into. Their rows and the files
     * in their place do not belong together then, since the other installation writes there too or
     * the rows have not all arrived. A station standing on storage it took over from the installation
     * it moved here from has its counters corrected but never loses a file, since the files there
     * that no row here names can still be that installation's. Both are asked again before every
     * category, so a transfer that begins while the station is being reconciled stops it there.
     *
     * @param stationId the station to reconcile
     */
    public void reconcileStation(int stationId) {
        try {
            UUID stationUid = stationRepository.resolveUid(stationId);
            if (stationUid == null) {
                log.warn("Skipping reconciliation for station {} - no UUID resolved", stationId);
                return;
            }
            var scope = new StorageScope.Station(stationId, stationUid);
            for (StorageCategory category : STATION_CATEGORIES) {
                if (heldByTransfer(stationId)) {
                    log.info("Skipping reconciliation for station {}, which a transfer holds", stationId);
                    return;
                }
                try {
                    reconcileCategory(stationId, scope, category, !configRepository.isSharedByTransfer(stationId));
                } catch (Exception e) {
                    log.error("Error reconciling category {} for station {}", category, stationId, e);
                }
            }
            reconcileAvatars(stationId);
        } catch (Exception e) {
            log.error("Error reconciling storage for station {}", stationId, e);
        }
    }

    private boolean heldByTransfer(int stationId) {
        if (activeImports.isUnderway(stationId)) return true;
        return query("""
                        SELECT moved_away_at IS NOT NULL OR read_only_for_transfer AS held
                          FROM station
                         WHERE id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getBoolean("held"))
                .first()
                .orElse(true);
    }

    private void reconcileCategory(
            int stationId, StorageScope.Station scope, StorageCategory category, boolean mayDelete) {
        if (mayDelete) deleteOrphans(stationId, scope, category);
        if (!category.tracksUsage()) return;
        long totalBytes = storage.sumSize(scope, category);
        int fileCount = storage.listKeys(scope, category, "").size();
        usageRepository.setUsage(stationId, category, totalBytes, fileCount);
    }

    /**
     * Removes files whose owning database row no longer exists. Skips silently for categories
     * whose objects are not tracked in their own table (KB inline images, for example, are only
     * referenced from free-form markdown). The alive set is queried once per category; on a
     * query failure the exception propagates, so a transient database hiccup never sweeps live
     * files away.
     */
    private void deleteOrphans(int stationId, StorageScope.Station scope, StorageCategory category) {
        Optional<Set<String>> aliveOpt = aliveIdentitiesFor(stationId, category);
        if (aliveOpt.isEmpty()) return;
        Set<String> alive = aliveOpt.get();
        List<String> onDisk = storage.listKeys(scope, category, "");
        if (onDisk.isEmpty()) return;
        Set<String> orphanIdentities = new LinkedHashSet<>();
        for (String key : onDisk) {
            int slash = key.indexOf('/');
            String identity = slash < 0 ? key : key.substring(0, slash);
            if (!alive.contains(identity)) orphanIdentities.add(identity);
        }
        if (orphanIdentities.isEmpty()) return;
        log.info(
                "Reconciliation removing {} orphan identity prefix(es) under station {} / {}",
                orphanIdentities.size(),
                stationId,
                category);
        for (String identity : orphanIdentities) {
            try {
                storage.deletePrefix(scope, category, identity);
            } catch (Exception e) {
                log.warn(
                        "Failed to delete orphan storage under station {} / {} / {}", stationId, category, identity, e);
            }
        }
    }

    /**
     * Returns the set of identity-prefix strings that match a live database row for the given
     * station + category. Identity is the leading path segment on disk: for {@code MEDIA_FILES}
     * it is the content hash, for {@code IMAGE_KB_ICON} the {@code folder-<id>} key, and for
     * the other IMAGE_* / KB_FILES tables the bare entity id. Returns {@link Optional#empty()}
     * for categories that are not safely cleanable from this central place.
     */
    private Optional<Set<String>> aliveIdentitiesFor(int stationId, StorageCategory category) {
        return switch (category) {
            case MEDIA_FILES ->
                Optional.of(queryIdentitySet(
                        "SELECT DISTINCT content_hash FROM station_file WHERE station_id = :station_id;",
                        stationId,
                        "content_hash"));
            case KB_FILES ->
                Optional.of(queryIdentitySet(
                        "SELECT id::text AS identity FROM kb_file WHERE station_id = :station_id;",
                        stationId,
                        "identity"));
            case IMAGE_LOST_AND_FOUND -> Optional.of(queryIdentitySet("""
                    SELECT id::text AS identity
                      FROM lost_and_found_item
                     WHERE station_id = :station_id;""", stationId, "identity"));
            case IMAGE_QUIZ_QUESTION -> Optional.of(queryIdentitySet("""
                    SELECT q.id::text AS identity
                      FROM quiz_question q
                      JOIN quiz_catalog c ON c.id = q.catalog_id
                     WHERE c.station_id = :station_id;""", stationId, "identity"));
            case IMAGE_KB_ICON -> Optional.of(queryIdentitySet("""
                    SELECT 'folder-' || id::text AS identity
                      FROM kb_folder
                     WHERE station_id = :station_id;""", stationId, "identity"));
            case IMAGE_KB_FILE_PICTURE -> Optional.of(queryIdentitySet("""
                    SELECT 'file-' || id::text AS identity
                      FROM kb_file
                     WHERE station_id = :station_id;""", stationId, "identity"));
            default -> Optional.empty();
        };
    }

    private Set<String> queryIdentitySet(String sql, int stationId, String column) {
        var values = query(sql)
                .single(call().bind("station_id", stationId))
                .map(row -> row.getString(column))
                .all();
        return new LinkedHashSet<>(values);
    }

    /**
     * Avatars live in an account-scoped tree but the per-station usage row needs the bytes
     * attributed to every station the account is a member of. Sum each member's account-scope
     * avatar bytes and roll the result up into the station's IMAGE_AVATAR row.
     */
    private void reconcileAvatars(int stationId) {
        var accountUids = query("""
                SELECT DISTINCT a.uid FROM account a
                JOIN station_member sm ON sm.account_id = a.id
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

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "storage-reconciliation",
                Schedule.fixedDelay(Duration.ofMinutes(1), reconciliationInterval),
                this::reconcileAll));
    }
}
