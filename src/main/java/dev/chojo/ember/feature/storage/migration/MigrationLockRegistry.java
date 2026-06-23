/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.migration;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-{@code (station, category)} migration mutex. A migration acquires the lock at start and
 * releases it on success or failure; a second concurrent call to migrate the same target
 * fails fast with a clear error instead of producing partial duplicate writes.
 *
 * <p>The registry is in-memory by design — restarting Ember drops every lock. Concept §12
 * (migration tool) says the operation is idempotent and resumable: a re-run after a restart
 * walks the source again, skips keys already present on the target via {@code exists()}, and
 * finishes the job.
 */
@Singleton
public class MigrationLockRegistry {
    private final Set<Key> locks = ConcurrentHashMap.newKeySet();

    /** Attempts to acquire the lock for {@code (stationId, category)}; returns {@code false} when it is already held. */
    public boolean tryAcquire(int stationId, StorageCategory category) {
        return locks.add(new Key(stationId, category));
    }

    /** Releases the lock for {@code (stationId, category)}. No-op when not held. */
    public void release(int stationId, StorageCategory category) {
        locks.remove(new Key(stationId, category));
    }

    /** Whether a migration is currently in flight for {@code (stationId, category)}. */
    public boolean isLocked(int stationId, StorageCategory category) {
        return locks.contains(new Key(stationId, category));
    }

    private record Key(int stationId, StorageCategory category) {}
}
