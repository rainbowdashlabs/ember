/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Moves one station's bytes to another backend. */
@Singleton
public class StorageMigrationService {
    private static final Logger log = LoggerFactory.getLogger(StorageMigrationService.class);

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

    /** Moves a station onto a backend of its own. */
    public MigrationResult migrate(int stationId, StationStorageBackendConfig targetConfig) {
        return moveStation(stationId, new Destination.Own(targetConfig));
    }

    /** Moves a station back to the instance default; nothing happens when it already stands there. */
    public MigrationResult migrateToInstanceDefault(int stationId) {
        return moveStation(stationId, new Destination.InstanceDefault());
    }

    /**
     * Under the station's lock: probes the target, copies and verifies every movable station key,
     * records where the station now stands, checks a sample, and deletes the source. A target built for
     * the move is closed afterwards; the instance default is shared and stays open.
     *
     * @throws MigrationException on any failure, after the lock was released, so the caller may retry
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
            StorageBackend target = buildTarget(destination);
            try {
                HealthStatus probe = target.probe();
                if (!probe.healthy()) {
                    throw new MigrationException(
                            "Target probe failed: " + probe.error().orElse("unknown error"));
                }
                return run(scope, target, destination);
            } finally {
                if (!(destination instanceof Destination.InstanceDefault)) target.close();
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
        var stats = BackendCopy.Stats.NONE;
        var allCopiedKeys = new ArrayList<String>();
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

            stats = stats.plus(BackendCopy.copyAll(source, target, keys));
            allCopiedKeys.addAll(keys);
            perCategoryKeys.add(new CategoryKeys(source, keys));
        }

        record(scope.stationId(), destination);
        Optional<StorageBackend> formerOwn = resolver.detachStation(scope.stationId());
        try {
            BackendCopy.sampleVerify(target, allCopiedKeys);

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
            return new MigrationResult(stats.total(), stats.copied(), stats.skipped(), deletedCount, stats.bytes());
        } finally {
            formerOwn.ifPresent(StorageBackend::close);
        }
    }

    /** @param skipped keys already on the target with a matching SHA-256 */
    public record MigrationResult(int totalKeys, int copied, int skipped, int deleted, long copiedBytes) {}

    /** Where a station's bytes are going; configs carry their credentials encrypted. */
    public sealed interface Destination {
        /** A backend the station brings itself. */
        record Own(StationStorageBackendConfig config) implements Destination {}

        /** @param configId the version of the cluster's storage, so the placement records what was carried to */
        record Cluster(int clusterId, int configId, StationStorageBackendConfig config) implements Destination {}

        /** Whatever the instance provides, which is the absence of both other rows. */
        record InstanceDefault() implements Destination {}
    }

    private record CategoryKeys(StorageBackend source, List<String> keys) {}
}
