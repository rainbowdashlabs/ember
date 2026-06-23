/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.migration;

import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StoredStream;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.InstanceStorageReadOnlyState;
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
import java.util.Set;

/**
 * Walks every {@code (scope, category)} whose resolver outcome is the instance default and
 * copies the bytes from that backend onto an operator-supplied target. Drives the byte-copy
 * primitive used by the instance-wide backend swap described in concept §19.6.
 *
 * <p>Lock + read-only flag are held for the duration of the copy; the actual YAML flip,
 * cached-backend invalidation, and audit emission are layered on top by the route handler
 * (Phase 18.C) which composes this primitive with {@code Configurations.save()} and
 * {@code StorageBackendFactory#invalidateInstanceDefault()}.
 *
 * <p>Idempotent: a key already present on the target with a matching SHA-256 is skipped, so a
 * crashed migration can be re-run and will pick up where it left off.
 */
@Singleton
public class InstanceStorageMigrationService {
    private static final Logger log = LoggerFactory.getLogger(InstanceStorageMigrationService.class);

    /**
     * Fraction of migrated keys re-read on the target as a sanity check (1%).
     */
    private static final int SAMPLE_DENOMINATOR = 100;

    private final StationRepository stationRepository;
    private final AccountRepository accountRepository;
    private final StationStorageConfigRepository configRepository;
    private final StorageBackendFactory factory;
    private final MigrationLockRegistry locks;
    private final InstanceStorageReadOnlyState readOnly;

    @Inject
    public InstanceStorageMigrationService(
            StationRepository stationRepository,
            AccountRepository accountRepository,
            StationStorageConfigRepository configRepository,
            StorageBackendFactory factory,
            MigrationLockRegistry locks,
            InstanceStorageReadOnlyState readOnly) {
        this.stationRepository = stationRepository;
        this.accountRepository = accountRepository;
        this.configRepository = configRepository;
        this.factory = factory;
        this.locks = locks;
        this.readOnly = readOnly;
    }

    /**
     * Acquires the instance lock, flips the read-only flag, probes {@code target}, copies every
     * key that currently resolves to the instance default onto {@code target}, sample-verifies
     * the result, and returns the per-source key list so the caller can delete it after the
     * YAML flip. Lock and read-only flag remain held; the caller is required to call exactly
     * one of {@link #commit} or {@link #abort} on the returned handle.
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
            if (source == target) {
                target.close();
                throw new MigrationException("Target backend resolves to the current instance default");
            }
            try {
                CopyOutcome outcome = run(source, target);
                return new PreparedMigration(target, outcome);
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

    private CopyOutcome run(StorageBackend source, StorageBackend target) {
        int totalKeys = 0;
        int copiedCount = 0;
        int skippedCount = 0;
        long copiedBytes = 0;
        var allKeys = new ArrayList<String>();
        var perScopeKeys = new ArrayList<ScopeKeys>();

        Set<Integer> stationsWithOverride = configRepository.findAllStationIds();
        for (Station station : stationRepository.findAll()) {
            if (stationsWithOverride.contains(station.id())) continue;
            var scope = new StorageScope.Station(station.id(), station.uid());
            for (StorageCategory category : StorageCategory.values()) {
                if (!isInstanceDefaultStationCategory(category)) continue;
                var stats =
                        copyCategory(source, target, scope.prefix() + "/" + category.prefix(), allKeys, perScopeKeys);
                totalKeys += stats.total;
                copiedCount += stats.copied;
                skippedCount += stats.skipped;
                copiedBytes += stats.bytes;
            }
        }

        for (StorageCategory category : StorageCategory.values()) {
            if (!isInstanceDefaultInstanceCategory(category)) continue;
            var stats = copyCategory(
                    source,
                    target,
                    new StorageScope.Instance().prefix() + "/" + category.prefix(),
                    allKeys,
                    perScopeKeys);
            totalKeys += stats.total;
            copiedCount += stats.copied;
            skippedCount += stats.skipped;
            copiedBytes += stats.bytes;
        }

        for (Account account : accountRepository.findAll()) {
            var scope = new StorageScope.Account(account.uid());
            var stats = copyCategory(
                    source,
                    target,
                    scope.prefix() + "/" + StorageCategory.IMAGE_AVATAR.prefix(),
                    allKeys,
                    perScopeKeys);
            totalKeys += stats.total;
            copiedCount += stats.copied;
            skippedCount += stats.skipped;
            copiedBytes += stats.bytes;
        }

        int sampleSize = Math.max(1, allKeys.size() / SAMPLE_DENOMINATOR);
        sampleVerify(target, allKeys, sampleSize);
        return new CopyOutcome(totalKeys, copiedCount, skippedCount, copiedBytes, perScopeKeys);
    }

    private CopyStats copyCategory(
            StorageBackend source,
            StorageBackend target,
            String prefix,
            List<String> allKeysSink,
            List<ScopeKeys> perScopeSink) {
        List<String> keys = source.listByPrefix(prefix);
        if (keys.isEmpty()) return new CopyStats(0, 0, 0, 0L);
        log.info("Migrating {} keys under {}", keys.size(), prefix);
        int total = 0;
        int copied = 0;
        int skipped = 0;
        long bytes = 0;
        for (String key : keys) {
            total++;
            if (target.exists(key) && hashesMatch(target, source, key)) {
                skipped++;
                allKeysSink.add(key);
                continue;
            }
            bytes += copyOne(source, target, key);
            copied++;
            allKeysSink.add(key);
        }
        perScopeSink.add(new ScopeKeys(keys));
        return new CopyStats(total, copied, skipped, bytes);
    }

    private static boolean isInstanceDefaultStationCategory(StorageCategory category) {
        if (category.isLocalPinned()) return false;
        if (category.scopeKind() != StorageScope.Kind.STATION) return false;
        if (!category.isMovable()) return false;
        return !StorageCategory.LEGACY_CATEGORIES.contains(category);
    }

    private static boolean isInstanceDefaultInstanceCategory(StorageCategory category) {
        if (category.isLocalPinned()) return false;
        if (category.scopeKind() != StorageScope.Kind.INSTANCE) return false;
        if (!category.isMovable()) return false;
        return !StorageCategory.LEGACY_CATEGORIES.contains(category);
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
        if (copiedKeys.isEmpty()) return;
        int step = Math.max(1, copiedKeys.size() / sampleSize);
        for (int i = 0; i < copiedKeys.size() && i < sampleSize * step; i += step) {
            String key = copiedKeys.get(i);
            if (!target.exists(key)) {
                throw new MigrationException("Sample verification failed: target missing key " + key);
            }
        }
    }

    /**
     * Hand back to a {@link PreparedMigration} once the caller has flipped the YAML and
     * invalidated the factory cache. Deletes the source bytes when {@code keepSource} is
     * {@code false}, then clears the read-only flag and releases the instance lock.
     */
    public MigrationResult commit(PreparedMigration prepared, boolean keepSource) {
        int deleted = 0;
        try (StorageBackend ignored = prepared.target()) {
            if (!keepSource) {
                StorageBackend source = factory.instanceDefault();
                for (ScopeKeys scope : prepared.outcome().perScopeKeys()) {
                    for (String key : scope.keys()) {
                        try {
                            source.delete(key);
                            deleted++;
                        } catch (Exception e) {
                            log.warn("Failed to delete migrated source key {}", key, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to close target after commit", e);
        } finally {
            readOnly.unlock();
            locks.releaseInstance();
        }
        var o = prepared.outcome();
        return new MigrationResult(o.totalKeys(), o.copied(), o.skipped(), deleted, o.copiedBytes());
    }

    /**
     * Release the lock and read-only flag without flipping the YAML — the previous backend
     * stays authoritative. Called by the route handler when the YAML save itself fails after a
     * successful byte copy.
     */
    public void abort(PreparedMigration prepared) {
        try {
            prepared.target().close();
        } catch (Exception e) {
            log.warn("Failed to close target after abort", e);
        }
        readOnly.unlock();
        locks.releaseInstance();
    }

    /**
     * Handle returned by {@link #prepare}. The caller MUST call exactly one of
     * {@link #commit} or {@link #abort} on this object so the read-only flag and lock are
     * released. {@code target} is a live backend instance holding its own connection pool —
     * closed by {@code commit}/{@code abort}.
     */
    public record PreparedMigration(StorageBackend target, CopyOutcome outcome) {}

    /**
     * Result summary returned by {@link #commit}.
     */
    public record MigrationResult(int totalKeys, int copied, int skipped, int deleted, long copiedBytes) {}

    /**
     * Internal accumulator: what the byte-copy loop produced before commit / abort.
     */
    public record CopyOutcome(int totalKeys, int copied, int skipped, long copiedBytes, List<ScopeKeys> perScopeKeys) {}

    private record CopyStats(int total, int copied, int skipped, long bytes) {}

    private record ScopeKeys(List<String> keys) {}
}
