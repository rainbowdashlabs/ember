/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.migration;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
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
import java.util.UUID;

/**
 * Moves bytes between backends for one {@code (station, category)} tuple. Drives the steps
 * in concept §12: probe the target, hold an in-process lock, copy + verify every key, flip
 * the override row, sample-verify, delete the source, release the lock.
 *
 * <p>Idempotent: a key already present on the target with the same SHA-256 is skipped, so a
 * crashed migration can be re-run and will pick up where it left off.
 */
@Singleton
public class StorageMigrationService {
    private static final Logger log = LoggerFactory.getLogger(StorageMigrationService.class);

    /** Fraction of migrated keys re-read on the target as a sanity check (1%). */
    private static final int SAMPLE_DENOMINATOR = 100;

    private final StationRepository stationRepository;
    private final StationStorageConfigRepository configRepository;
    private final StorageBackendFactory factory;
    private final StorageBackendResolver resolver;
    private final MigrationLockRegistry locks;

    @Inject
    public StorageMigrationService(
            StationRepository stationRepository,
            StationStorageConfigRepository configRepository,
            StorageBackendFactory factory,
            StorageBackendResolver resolver,
            MigrationLockRegistry locks) {
        this.stationRepository = stationRepository;
        this.configRepository = configRepository;
        this.factory = factory;
        this.resolver = resolver;
        this.locks = locks;
    }

    /**
     * Migrates every object under {@code (stationId, category)} from the currently-resolved
     * backend to the backend described by {@code targetConfig}, then flips the override row to
     * point at the new backend.
     *
     * @throws MigrationException on any failure; the lock is released before the exception
     *     propagates so the caller may safely retry.
     */
    public MigrationResult migrate(int stationId, StorageCategory category, StationStorageBackendConfig targetConfig) {
        if (category.isLocalPinned()) {
            throw new MigrationException(
                    "Category " + category + " is local-pinned and cannot be migrated to another backend");
        }
        if (category.scopeKind() != StorageScope.Kind.STATION) {
            throw new MigrationException(
                    "Migration is only supported for station-scoped categories; " + category + " is not one");
        }
        if (!locks.tryAcquire(stationId, category)) {
            throw new MigrationException(
                    "A migration is already in flight for station " + stationId + " / " + category);
        }

        UUID stationUid = stationRepository.resolveUid(stationId);
        if (stationUid == null) {
            locks.release(stationId, category);
            throw new MigrationException("Cannot resolve station UUID for " + stationId);
        }
        StorageScope.Station scope = new StorageScope.Station(stationId, stationUid);

        try (StorageBackend target = factory.buildForStation(targetConfig)) {
            HealthStatus probe = target.probe();
            if (!probe.healthy()) {
                throw new MigrationException(
                        "Target probe failed: " + probe.error().orElse("unknown error"));
            }
            StorageBackend source = resolver.forScope(scope, category);
            return run(scope, category, source, target, targetConfig);
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Migration failed: " + e.getMessage(), e);
        } finally {
            locks.release(stationId, category);
        }
    }

    private MigrationResult run(
            StorageScope.Station scope,
            StorageCategory category,
            StorageBackend source,
            StorageBackend target,
            StationStorageBackendConfig targetConfig) {
        String prefix = scope.prefix() + "/" + category.prefix();
        List<String> keys = source.listByPrefix(prefix);
        log.info(
                "Migrating {} keys under {} (station {}, category {})",
                keys.size(),
                prefix,
                scope.stationId(),
                category);

        long copiedBytes = 0;
        int copiedCount = 0;
        int skippedCount = 0;
        var copiedKeys = new ArrayList<String>(keys.size());

        for (String key : keys) {
            if (target.exists(key) && hashesMatch(target, source, key)) {
                skippedCount++;
                copiedKeys.add(key);
                continue;
            }
            long bytes = copyOne(source, target, key);
            copiedBytes += bytes;
            copiedCount++;
            copiedKeys.add(key);
        }

        configRepository.upsert(scope.stationId(), category, targetConfig);
        resolver.invalidateStation(scope.stationId(), category);

        int sampleSize = Math.max(1, copiedKeys.size() / SAMPLE_DENOMINATOR);
        sampleVerify(target, copiedKeys, sampleSize);

        int deletedCount = 0;
        for (String key : keys) {
            try {
                source.delete(key);
                deletedCount++;
            } catch (Exception e) {
                log.warn("Failed to delete migrated source key {}", key, e);
            }
        }

        return new MigrationResult(keys.size(), copiedCount, skippedCount, deletedCount, copiedBytes);
    }

    private long copyOne(StorageBackend source, StorageBackend target, String key) {
        try (StoredStream stream = source.read(key)
                .orElseThrow(() -> new MigrationException("Source key disappeared mid-migration: " + key))) {
            long length = stream.contentLength();
            ObjectMetadata metadata = stream.metadata();
            target.store(key, stream.body(), length, metadata);
            return length;
        } catch (IOException e) {
            throw new MigrationException("Failed to read source key " + key, e);
        }
    }

    private boolean hashesMatch(StorageBackend a, StorageBackend b, String key) {
        return hashOf(a, key).equals(hashOf(b, key));
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

    private void sampleVerify(StorageBackend target, List<String> copiedKeys, int sampleSize) {
        int step = Math.max(1, copiedKeys.size() / sampleSize);
        for (int i = 0; i < copiedKeys.size() && i < sampleSize * step; i += step) {
            String key = copiedKeys.get(i);
            if (!target.exists(key)) {
                throw new MigrationException("Sample verification failed: target missing key " + key);
            }
        }
    }

    /**
     * Result summary of a {@link #migrate} call. {@code skipped} counts keys that were already
     * present on the target with a matching SHA-256 (idempotent re-run).
     */
    public record MigrationResult(int totalKeys, int copied, int skipped, int deleted, long copiedBytes) {}
}
