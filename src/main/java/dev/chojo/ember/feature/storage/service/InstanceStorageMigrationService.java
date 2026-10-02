/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies every key that resolves to the instance default onto a new instance backend, for the
 * instance-wide swap; the route switches the configuration and writes the audit around it.
 */
@Singleton
public class InstanceStorageMigrationService {
    private static final Logger log = LoggerFactory.getLogger(InstanceStorageMigrationService.class);

    private final StationRepository stationRepository;
    private final AccountRepository accountRepository;
    private final StationStorageConfigRepository configRepository;
    private final ClusterStationStorageRepository placementRepository;
    private final StorageBackendFactory factory;
    private final MigrationLockRegistry locks;
    private final InstanceStorageReadOnlyState readOnly;

    @Inject
    public InstanceStorageMigrationService(
            StationRepository stationRepository,
            AccountRepository accountRepository,
            StationStorageConfigRepository configRepository,
            ClusterStationStorageRepository placementRepository,
            StorageBackendFactory factory,
            MigrationLockRegistry locks,
            InstanceStorageReadOnlyState readOnly) {
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.configRepository = configRepository;
        this.placementRepository = placementRepository;
        this.factory = factory;
        this.locks = locks;
        this.readOnly = readOnly;
    }

    /**
     * Takes the instance lock and the read-only flag, probes the target, copies and sample-checks every
     * key. Both stay held until the caller calls exactly one of {@link #commit} or {@link #abort}.
     *
     * <p>A target at the destination the instance already stands on, as when only its credentials change,
     * copies nothing and leaves nothing for the commit to delete: its keys are the instance's own files.
     */
    public PreparedMigration prepare(StorageBackendSettings targetSettings) {
        if (!locks.tryAcquireInstance()) {
            throw new MigrationException("Another migration is already in flight (per-station or instance-wide)");
        }
        if (!readOnly.lock()) {
            locks.releaseInstance();
            throw new MigrationException("Instance read-only flag is already set");
        }
        try {
            StorageBackend target = factory.buildForInstance(targetSettings);
            HealthStatus probe = target.probe();
            if (!probe.healthy()) {
                target.close();
                throw new MigrationException(
                        "Target probe failed: " + probe.error().orElse("unknown error"));
            }
            StorageBackend source = factory.instanceDefault();
            try {
                CopyOutcome outcome = source.sharesDestinationWith(target) ? CopyOutcome.NOTHING : run(source, target);
                return new PreparedMigration(source, target, outcome);
            } catch (RuntimeException e) {
                target.close();
                throw e;
            }
        } catch (RuntimeException e) {
            readOnly.unlock();
            locks.releaseInstance();
            throw e;
        } catch (Exception e) {
            readOnly.unlock();
            locks.releaseInstance();
            throw new MigrationException("Migration preparation failed: " + e.getMessage(), e);
        }
    }

    /** Copies what stands on the instance default; a station on a backend of its own or its cluster's is skipped. */
    private CopyOutcome run(StorageBackend source, StorageBackend target) {
        var keys = new ArrayList<String>();
        var stats = BackendCopy.Stats.NONE;
        Set<Integer> elsewhere = new HashSet<>(configRepository.findAllStationIds());
        elsewhere.addAll(placementRepository.findAllStationIds());
        for (Station station : stationRepository.findAll()) {
            if (elsewhere.contains(station.id())) continue;
            var scope = new StorageScope.Station(station.id(), station.uid());
            for (StorageCategory category : movable(StorageScope.Kind.STATION)) {
                stats = stats.plus(copyCategory(source, target, scope, category, keys));
            }
        }
        for (StorageCategory category : movable(StorageScope.Kind.INSTANCE)) {
            stats = stats.plus(copyCategory(source, target, new StorageScope.Instance(), category, keys));
        }
        for (Account account : accountRepository.findAll()) {
            var scope = new StorageScope.Account(account.uid());
            stats = stats.plus(copyCategory(source, target, scope, StorageCategory.IMAGE_AVATAR, keys));
        }
        BackendCopy.sampleVerify(target, keys);
        return new CopyOutcome(stats, keys);
    }

    private static List<StorageCategory> movable(StorageScope.Kind kind) {
        return Arrays.stream(StorageCategory.values())
                .filter(category -> category.isMovable() && category.scopeKind() == kind)
                .toList();
    }

    private BackendCopy.Stats copyCategory(
            StorageBackend source,
            StorageBackend target,
            StorageScope scope,
            StorageCategory category,
            List<String> keysSink) {
        String prefix = scope.prefix() + "/" + category.prefix();
        List<String> keys = source.listByPrefix(prefix);
        if (keys.isEmpty()) return BackendCopy.Stats.NONE;
        log.info("Migrating {} keys under {}", keys.size(), prefix);
        var stats = BackendCopy.copyAll(source, target, keys);
        keysSink.addAll(keys);
        return stats;
    }

    /**
     * Finishes a move once the caller has switched the configuration and dropped the cached default:
     * deletes the source bytes unless {@code keepSource}, closes both backends and releases the lock.
     */
    public MigrationResult commit(PreparedMigration prepared, boolean keepSource) {
        int deleted = 0;
        try {
            if (!keepSource) {
                for (String key : prepared.outcome().keys()) {
                    try {
                        prepared.source().delete(key);
                        deleted++;
                    } catch (Exception e) {
                        log.warn("Failed to delete migrated source key {}", key, e);
                    }
                }
            }
        } finally {
            closeQuietly(prepared.target(), "target after commit");
            closeQuietly(prepared.source(), "source after commit");
            readOnly.unlock();
            locks.releaseInstance();
        }
        var stats = prepared.outcome().stats();
        return new MigrationResult(stats.total(), stats.copied(), stats.skipped(), deleted, stats.bytes());
    }

    /** Releases the lock without switching; the previous backend stays in use. */
    public void abort(PreparedMigration prepared) {
        closeQuietly(prepared.target(), "target after abort");
        readOnly.unlock();
        locks.releaseInstance();
    }

    private static void closeQuietly(StorageBackend backend, String which) {
        try {
            backend.close();
        } catch (Exception e) {
            log.warn("Failed to close {}", which, e);
        }
    }

    public boolean isMigrationInFlight() {
        return readOnly.isLocked();
    }

    /**
     * What {@link #prepare} hands over for exactly one {@link #commit} or {@link #abort}. The source is
     * captured here, so the commit still deletes from it after the cached default was dropped.
     */
    public record PreparedMigration(StorageBackend source, StorageBackend target, CopyOutcome outcome) {}

    public record MigrationResult(int totalKeys, int copied, int skipped, int deleted, long copiedBytes) {}

    /** What the copy produced, with every key it covered. */
    record CopyOutcome(BackendCopy.Stats stats, List<String> keys) {
        static final CopyOutcome NOTHING = new CopyOutcome(BackendCopy.Stats.NONE, List.of());
    }
}
