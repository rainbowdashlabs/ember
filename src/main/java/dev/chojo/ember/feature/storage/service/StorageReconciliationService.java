/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Recalculates actual storage usage per station by querying the database and walking the filesystem.
 * Runs on a configurable schedule (default daily at 03:00) and can be triggered manually by admins.
 */
@Singleton
public class StorageReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(StorageReconciliationService.class);

    private final StorageUsageRepository usageRepository;
    private final StationRepository stationRepository;

    @Inject
    public StorageReconciliationService(
            StorageUsageRepository usageRepository, StationRepository stationRepository, Storage storageConfig) {
        this.usageRepository = usageRepository;
        this.stationRepository = stationRepository;

        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "storage-reconciliation");
            t.setDaemon(true);
            return t;
        });
        int intervalHours = storageConfig.reconciliationIntervalHours();
        // Run immediately on startup to backfill usage data, then repeat at the configured interval
        scheduler.scheduleWithFixedDelay(this::reconcileAll, 0, intervalHours, TimeUnit.HOURS);
    }

    /**
     * Reconciles storage usage for all stations.
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
     * Reconciles storage usage for a single station.
     */
    public void reconcileStation(int stationId) {
        try {
            reconcileKbFiles(stationId);
            reconcileBoardAttachments(stationId);
            reconcilePageImages(stationId);
            reconcileImages(stationId);
            reconcileAvatars(stationId);
        } catch (Exception e) {
            log.error("Error reconciling storage for station {}", stationId, e);
        }
    }

    private void reconcileKbFiles(int stationId) {
        var result = query("""
                SELECT COALESCE(SUM(file_size), 0) AS total_bytes, COUNT(*) AS file_count
                FROM kb_file
                WHERE station_id = :station_id AND file_size > 0;
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new UsageResult(row.getLong("total_bytes"), row.getInt("file_count")))
                .first()
                .orElse(new UsageResult(0, 0));
        usageRepository.setUsage(stationId, StorageCategory.KB_FILES, result.totalBytes(), result.fileCount());
    }

    private void reconcileBoardAttachments(int stationId) {
        var result = query("""
                SELECT COALESCE(SUM(a.size_bytes), 0) AS total_bytes, COUNT(*) AS file_count
                FROM board_ticket_attachment a
                JOIN board_ticket t ON t.id = a.ticket_id
                JOIN board b ON b.id = t.board_id
                WHERE b.station_id = :station_id;
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new UsageResult(row.getLong("total_bytes"), row.getInt("file_count")))
                .first()
                .orElse(new UsageResult(0, 0));
        usageRepository.setUsage(stationId, StorageCategory.BOARD_ATTACHMENTS, result.totalBytes(), result.fileCount());
    }

    private void reconcilePageImages(int stationId) {
        var result = query("""
                SELECT COALESCE(SUM(pi.file_size), 0) AS total_bytes, COUNT(*) AS file_count
                FROM page_image pi
                JOIN station_page sp ON sp.id = pi.page_id
                WHERE sp.station_id = :station_id;
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> new UsageResult(row.getLong("total_bytes"), row.getInt("file_count")))
                .first()
                .orElse(new UsageResult(0, 0));
        usageRepository.setUsage(stationId, StorageCategory.PAGE_IMAGES, result.totalBytes(), result.fileCount());
    }

    private void reconcileImages(int stationId) {
        // Images are stored on filesystem at data/images/{category}/{id}/
        // We need to map image IDs back to stations through entity tables
        long totalBytes = 0;
        int fileCount = 0;

        // KB icons: data/images/kb-icons/folder-{folderId}/
        var kbIconResult = query("""
                SELECT f.id FROM kb_file f
                WHERE f.station_id = :station_id AND f.folder_id IS NULL AND f.file_type = 'IMAGE';
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .all();
        // Also get folders with icons
        var folderIds = query("""
                SELECT DISTINCT kff.id FROM kb_file kff
                WHERE kff.station_id = :station_id AND kff.file_type = 'MARKDOWN';
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .all();

        // For simplicity, calculate image sizes from filesystem walk for station-specific paths
        // KB icons are at data/images/kb-icons/folder-{folderId}
        for (int folderId : folderIds) {
            var size = directorySize(Path.of("data", "images", "kb-icons", "folder-" + folderId));
            totalBytes += size.totalBytes();
            fileCount += size.fileCount();
        }

        // KB inline images are at data/images/kb-images/{fileId}-{imageId}
        var kbFileIds = query("SELECT id FROM kb_file WHERE station_id = :station_id;")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .all();
        Path kbImagesDir = Path.of("data", "images", "kb-images");
        if (Files.exists(kbImagesDir)) {
            for (int fileId : kbFileIds) {
                String prefix = fileId + "-";
                try (Stream<Path> paths = Files.list(kbImagesDir)) {
                    for (Path dir : paths.filter(p -> p.getFileName().toString().startsWith(prefix))
                            .toList()) {
                        var size = directorySize(dir);
                        totalBytes += size.totalBytes();
                        fileCount += size.fileCount();
                    }
                } catch (IOException e) {
                    log.warn("Failed to scan KB images for station {}", stationId, e);
                }
            }
        }

        // Quiz question images: data/images/quiz-questions/{questionId}
        var questionIds = query("""
                SELECT qq.id FROM quiz_question qq
                JOIN quiz_catalog qc ON qc.id = qq.catalog_id
                WHERE qc.station_id = :station_id;
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .all();
        for (int questionId : questionIds) {
            var size = directorySize(Path.of("data", "images", "quiz-questions", String.valueOf(questionId)));
            totalBytes += size.totalBytes();
            fileCount += size.fileCount();
        }

        // Lost and found images: data/images/lost-and-found/{itemId}
        var lostFoundIds = query("""
                SELECT id FROM lost_and_found_item WHERE station_id = :station_id;
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getInt("id"))
                .all();
        for (int itemId : lostFoundIds) {
            var size = directorySize(Path.of("data", "images", "lost-and-found", String.valueOf(itemId)));
            totalBytes += size.totalBytes();
            fileCount += size.fileCount();
        }

        usageRepository.setUsage(stationId, StorageCategory.IMAGES, totalBytes, fileCount);
    }

    private void reconcileAvatars(int stationId) {
        // Avatars: data/images/avatars/{memberUid}
        var memberUids = query("""
                SELECT sm.uid FROM station_member sm
                WHERE sm.station_id = :station_id;
                """)
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getString("uid"))
                .all();

        long totalBytes = 0;
        int fileCount = 0;
        for (String uid : memberUids) {
            var size = directorySize(Path.of("data", "images", "avatars", uid));
            totalBytes += size.totalBytes();
            fileCount += size.fileCount();
        }
        usageRepository.setUsage(stationId, StorageCategory.AVATARS, totalBytes, fileCount);
    }

    private UsageResult directorySize(Path dir) {
        if (!Files.exists(dir)) return new UsageResult(0, 0);
        long totalBytes = 0;
        int fileCount = 0;
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                totalBytes += Files.size(path);
                fileCount++;
            }
        } catch (IOException e) {
            log.warn("Failed to calculate directory size: {}", dir, e);
        }
        return new UsageResult(totalBytes, fileCount);
    }

    private record UsageResult(long totalBytes, int fileCount) {}
}
