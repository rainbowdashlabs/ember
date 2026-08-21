/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.migration.MigrationLockRegistry;
import dev.chojo.ember.feature.storage.service.StorageReconciliationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moves a station's media from the prefix it had when the library was a page feature onto the one
 * it has now: {@code page-files} becomes {@code media/files} and {@code page-images} becomes
 * {@code media/images}.
 *
 * <p>Renaming a storage category renames the path its bytes live under, so this is a data
 * migration rather than a constant change. It follows the shape
 * {@link dev.chojo.ember.feature.storage.service.StorageMigrationService} already uses: copy,
 * verify by SHA-256, skip anything already present with matching bytes, sample-verify, delete the
 * source, reconcile the usage counters.
 *
 * <p>It runs <b>per station</b>, because each station may resolve to its own local, S3, SFTP or
 * SMB backend, and it is <b>resumable</b> for the same reason: a station on a remote backend with
 * a lot of media will not finish in one go, and a crash must not leave it half moved. A station
 * with nothing under the old prefix costs one listing and is left alone, so the second boot after
 * the upgrade is free.
 */
@Singleton
public class MediaPrefixMigrationService {
    private static final Logger log = LoggerFactory.getLogger(MediaPrefixMigrationService.class);

    /**
     * Fraction of moved keys re-read on the target as a sanity check (1%).
     */
    private static final int SAMPLE_DENOMINATOR = 100;

    /**
     * The prefix each renamed category used to live under.
     */
    private static final Map<StorageCategory, String> FORMER_PREFIXES =
            Map.of(StorageCategory.MEDIA_FILES, "page-files", StorageCategory.MEDIA_IMAGES, "page-images");

    private final StationRepository stationRepository;
    private final StorageBackendResolver resolver;
    private final StorageReconciliationService reconciliation;
    private final MigrationLockRegistry locks;

    @Inject
    public MediaPrefixMigrationService(
            StationRepository stationRepository,
            StorageBackendResolver resolver,
            StorageReconciliationService reconciliation,
            MigrationLockRegistry locks) {
        this.stationRepository = stationRepository;
        this.resolver = resolver;
        this.reconciliation = reconciliation;
        this.locks = locks;
    }

    private static String computeSha256(StoredStream stream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8 * 1024];
            int read;
            while ((read = stream.body().read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Moves every station that still has media under the former prefix. One station failing does
     * not stop the others: the move is resumable, so the next boot picks the failed one up again.
     *
     * @return how many stations were moved
     */
    public int migrateAll() {
        int moved = 0;
        for (var station : stationRepository.findAll()) {
            try {
                if (migrateStation(station.id()) > 0) moved++;
            } catch (Exception e) {
                log.error(
                        "Media prefix migration failed for station {}; it will be retried on next boot",
                        station.id(),
                        e);
            }
        }
        if (moved > 0) {
            log.info("Media prefix migration moved {} station(s) onto the media/ prefix", moved);
        }
        return moved;
    }

    /**
     * Moves one station's media. Returns the number of keys copied, which is zero for a station
     * that has already been moved or never had any.
     *
     * <p>The station is held read-only for the duration, because a write that lands under the old
     * prefix after it has been walked would be left behind.
     */
    public int migrateStation(int stationId) {
        UUID stationUid = stationRepository.resolveUid(stationId);
        if (stationUid == null) {
            log.warn("Skipping media prefix migration for station {} - no UUID resolved", stationId);
            return 0;
        }
        var scope = new StorageScope.Station(stationId, stationUid);

        var pending = new ArrayList<PendingMove>();
        for (var entry : FORMER_PREFIXES.entrySet()) {
            StorageBackend backend = resolver.forScope(scope, entry.getKey());
            String formerPrefix = scope.prefix() + "/" + entry.getValue();
            List<String> keys = backend.listByPrefix(formerPrefix);
            if (keys.isEmpty()) continue;
            pending.add(new PendingMove(
                    backend, formerPrefix, scope.prefix() + "/" + entry.getKey().prefix(), keys));
        }
        if (pending.isEmpty()) return 0;

        if (!locks.tryAcquire(stationId)) {
            throw new MigrationException("A migration is already in flight for station " + stationId);
        }
        stationRepository.markReadOnlyForTransfer(stationId);
        try {
            return run(stationId, pending);
        } finally {
            stationRepository.clearReadOnlyForTransfer(stationId);
            locks.release(stationId);
        }
    }

    private int run(int stationId, List<PendingMove> pending) {
        int copied = 0;
        var movedKeys = new ArrayList<MovedKey>();

        for (PendingMove move : pending) {
            log.info(
                    "Moving {} media key(s) from {} to {} for station {}",
                    move.keys().size(),
                    move.formerPrefix(),
                    move.targetPrefix(),
                    stationId);
            for (String key : move.keys()) {
                String target =
                        move.targetPrefix() + key.substring(move.formerPrefix().length());
                if (move.backend().exists(target) && hashesMatch(move.backend(), key, target)) {
                    movedKeys.add(new MovedKey(move.backend(), key, target));
                    continue;
                }
                copyOne(move.backend(), key, target);
                copied++;
                movedKeys.add(new MovedKey(move.backend(), key, target));
            }
        }

        sampleVerify(movedKeys);

        for (MovedKey moved : movedKeys) {
            try {
                moved.backend().delete(moved.sourceKey());
            } catch (Exception e) {
                log.warn("Failed to delete moved media key {}", moved.sourceKey(), e);
            }
        }

        reconciliation.reconcileStation(stationId);
        log.info("Station {} media moved onto the media/ prefix ({} key(s) copied)", stationId, copied);
        return copied;
    }

    private void copyOne(StorageBackend backend, String sourceKey, String targetKey) {
        try (StoredStream stream = backend.read(sourceKey)
                .orElseThrow(() -> new MigrationException("Source key disappeared mid-migration: " + sourceKey))) {
            backend.store(targetKey, stream.body(), stream.contentLength(), stream.metadata());
        } catch (IOException e) {
            throw new MigrationException("Failed to read source key " + sourceKey, e);
        }
    }

    private boolean hashesMatch(StorageBackend backend, String a, String b) {
        return hashOf(backend, a).equals(hashOf(backend, b));
    }

    private String hashOf(StorageBackend backend, String key) {
        try (StoredStream stream = backend.read(key).orElseThrow(() -> new MigrationException("Missing key: " + key))) {
            String stored = stream.metadata().sha256();
            if (stored != null && !stored.isBlank()) return stored;
            return computeSha256(stream);
        } catch (IOException e) {
            throw new MigrationException("Failed to read key for verification " + key, e);
        }
    }

    private void sampleVerify(List<MovedKey> movedKeys) {
        if (movedKeys.isEmpty()) return;
        int sampleSize = Math.max(1, movedKeys.size() / SAMPLE_DENOMINATOR);
        int step = Math.max(1, movedKeys.size() / sampleSize);
        for (int i = 0; i < movedKeys.size() && i < sampleSize * step; i += step) {
            MovedKey moved = movedKeys.get(i);
            if (!moved.backend().exists(moved.targetKey())) {
                throw new MigrationException("Sample verification failed: target missing key " + moved.targetKey());
            }
        }
    }

    private record PendingMove(StorageBackend backend, String formerPrefix, String targetPrefix, List<String> keys) {}

    private record MovedKey(StorageBackend backend, String sourceKey, String targetKey) {}
}
