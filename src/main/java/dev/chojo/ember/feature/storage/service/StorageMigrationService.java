/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

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
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.migration.MigrationLockRegistry;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
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
 * Moves bytes between backends for one station: probe the
 * target, hold an in-process lock on the station, walk every station-scoped movable category,
 * copy + verify every key, flip the override row, sample-verify, delete the source, release
 * the lock.
 *
 * <p>Idempotent: a key already present on the target with the same SHA-256 is skipped, so a
 * crashed migration can be re-run and will pick up where it left off.
 */
@Singleton
public class StorageMigrationService {
    private static final Logger log = LoggerFactory.getLogger(StorageMigrationService.class);

    /**
     * Fraction of migrated keys re-read on the target as a sanity check (1%).
     */
    private static final int SAMPLE_DENOMINATOR = 100;

    private final StationRepository stationRepository;
    private final StationStorageConfigRepository configRepository;
    private final ClusterStationStorageRepository placementRepository;
    private final StorageBackendFactory factory;
    private final StorageBackendResolver resolver;
    private final MigrationLockRegistry locks;

    @Inject
    public StorageMigrationService(
            StationRepository stationRepository,
            StationStorageConfigRepository configRepository,
            ClusterStationStorageRepository placementRepository,
            StorageBackendFactory factory,
            StorageBackendResolver resolver,
            MigrationLockRegistry locks) {
        this.stationRepository = stationRepository;
        this.configRepository = configRepository;
        this.placementRepository = placementRepository;
        this.factory = factory;
        this.resolver = resolver;
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
     * Migrates every object under {@code stationId} (across every station-scoped movable
     * category) from the currently-resolved backend to the backend described by
     * {@code targetConfig}, then flips the override row to point at the new backend.
     *
     * @throws MigrationException on any failure; the lock is released before the exception
     *                            propagates so the caller may safely retry.
     */
    public MigrationResult migrate(int stationId, StationStorageBackendConfig targetConfig) {
        return moveStation(stationId, new Destination.Own(targetConfig));
    }

    /**
     * Inverse of {@link #migrate}: moves every key currently sitting on the station's remote
     * override backend back to the instance default, then drops the {@code station_storage_config}
     * row so the station resolves to the instance default again. No-op when the station has no
     * override.
     */
    public MigrationResult migrateToInstanceDefault(int stationId) {
        return moveStation(stationId, new Destination.InstanceDefault());
    }

    /**
     * Carries every movable station-scoped key to where the station is going, and records that it got there.
     *
     * <p>One method for all three destinations, because the order of the steps is the safe one and there is
     * no reason for three copies of it: acquire the per-station lock, probe the target, copy and verify every
     * key, record the new placement, sample-verify, delete the source, release. What differs between the
     * three is the destination's own backend and the single write that says where the station now stands.
     *
     * @param stationId   the station being moved
     * @param destination where its bytes are going
     * @return what was carried
     * @throws MigrationException on any failure; the lock is released before it propagates, so the caller may
     *                            retry, and nothing has half happened
     */
    public MigrationResult moveStation(int stationId, Destination destination) {
        if (!locks.tryAcquire(stationId)) {
            throw new MigrationException("A migration is already in flight for station " + stationId);
        }

        UUID stationUid = stationRepository.resolveUid(stationId);
        if (stationUid == null) {
            locks.release(stationId);
            throw new MigrationException("Cannot resolve station UUID for " + stationId);
        }
        StorageScope.Station scope = new StorageScope.Station(stationId, stationUid);

        try {
            if (destination instanceof Destination.InstanceDefault && standsOnNothing(stationId)) {
                return new MigrationResult(0, 0, 0, 0, 0L);
            }
            try (StorageBackend target = buildTarget(destination)) {
                HealthStatus probe = target.probe();
                if (!probe.healthy()) {
                    throw new MigrationException(
                            "Target probe failed: " + probe.error().orElse("unknown error"));
                }
                return run(scope, target, destination);
            }
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Migration failed: " + e.getMessage(), e);
        } finally {
            locks.release(stationId);
        }
    }

    /** Whether a station already resolves to the instance default, which makes a move there nothing to do. */
    private boolean standsOnNothing(int stationId) {
        return configRepository.findOne(stationId).isEmpty()
                && placementRepository.findByStation(stationId).isEmpty();
    }

    private StorageBackend buildTarget(Destination destination) {
        return switch (destination) {
            case Destination.Own own -> factory.buildForStation(own.config());
            case Destination.Cluster cluster -> factory.buildForStation(cluster.config());
            case Destination.InstanceDefault ignored -> factory.instanceDefault();
        };
    }

    /**
     * Writes where the station stands now, which is the only step the three destinations do differently.
     */
    private void record(int stationId, Destination destination) {
        switch (destination) {
            case Destination.Own own -> {
                placementRepository.remove(stationId);
                configRepository.upsert(stationId, own.config());
            }
            case Destination.Cluster cluster -> {
                configRepository.delete(stationId);
                placementRepository.place(stationId, cluster.clusterId(), cluster.configId());
            }
            case Destination.InstanceDefault ignored -> {
                configRepository.delete(stationId);
                placementRepository.remove(stationId);
            }
        }
    }

    private MigrationResult run(StorageScope.Station scope, StorageBackend target, Destination destination) {
        int totalKeys = 0;
        int copiedCount = 0;
        int skippedCount = 0;
        long copiedBytes = 0;
        var allCopiedKeys = new ArrayList<KeyOnBackend>();
        var perCategoryKeys = new ArrayList<CategoryKeys>();

        for (StorageCategory category : StorageCategory.values()) {
            if (category.isLocalPinned()) continue;
            if (category.scopeKind() != StorageScope.Kind.STATION) continue;

            StorageBackend source = resolver.forScope(scope, category);
            if (source == target) continue;

            String prefix = scope.prefix() + "/" + category.prefix();
            List<String> keys = source.listByPrefix(prefix);
            if (keys.isEmpty()) continue;
            log.info(
                    "Migrating {} keys under {} (station {}, category {})",
                    keys.size(),
                    prefix,
                    scope.stationId(),
                    category);

            for (String key : keys) {
                totalKeys++;
                if (target.exists(key) && hashesMatch(target, source, key)) {
                    skippedCount++;
                    allCopiedKeys.add(new KeyOnBackend(source, key));
                    continue;
                }
                copiedBytes += copyOne(source, target, key);
                copiedCount++;
                allCopiedKeys.add(new KeyOnBackend(source, key));
            }
            perCategoryKeys.add(new CategoryKeys(source, keys));
        }

        record(scope.stationId(), destination);
        resolver.invalidateStation(scope.stationId());

        int sampleSize = Math.max(1, allCopiedKeys.size() / SAMPLE_DENOMINATOR);
        sampleVerify(target, allCopiedKeys, sampleSize);

        int deletedCount = 0;
        for (CategoryKeys cat : perCategoryKeys) {
            for (String key : cat.keys()) {
                try {
                    cat.source().delete(key);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("Failed to delete migrated source key {}", key, e);
                }
            }
        }

        return new MigrationResult(totalKeys, copiedCount, skippedCount, deletedCount, copiedBytes);
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

    private void sampleVerify(StorageBackend target, List<KeyOnBackend> copiedKeys, int sampleSize) {
        if (copiedKeys.isEmpty()) return;
        int step = Math.max(1, copiedKeys.size() / sampleSize);
        for (int i = 0; i < copiedKeys.size() && i < sampleSize * step; i += step) {
            String key = copiedKeys.get(i).key();
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

    /**
     * Where a station's bytes are going, which is the one thing that differs between the three moves.
     */
    public sealed interface Destination {
        /**
         * A backend the station brings itself, which is what makes it bounded by nobody.
         *
         * @param config the backend, with its credentials already encrypted
         */
        record Own(StationStorageBackendConfig config) implements Destination {}

        /**
         * A version of its cluster's storage.
         *
         * @param clusterId the cluster whose storage it is
         * @param configId  the version, so the placement records what was actually carried to
         * @param config    that version's backend, with its credentials already encrypted
         */
        record Cluster(int clusterId, int configId, StationStorageBackendConfig config) implements Destination {}

        /**
         * Back to whatever the instance provides, which is the absence of both other rows.
         */
        record InstanceDefault() implements Destination {}
    }

    private record KeyOnBackend(StorageBackend source, String key) {}

    private record CategoryKeys(StorageBackend source, List<String> keys) {}
}
