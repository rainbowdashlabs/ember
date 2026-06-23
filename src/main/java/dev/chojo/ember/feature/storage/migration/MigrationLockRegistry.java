/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.migration;

import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-station migration mutex. A migration acquires the lock at start and releases it on
 * success or failure; a second concurrent call to migrate the same station fails fast with a
 * clear error instead of producing partial duplicate writes.
 *
 * <p>The registry is in-memory by design — restarting Ember drops every lock. A re-run after
 * a restart walks the source again, skips keys already present on the target via
 * {@code exists()} + SHA-256, and finishes the job.
 */
@Singleton
public class MigrationLockRegistry {
    private final Set<Integer> locks = ConcurrentHashMap.newKeySet();

    /** Attempts to acquire the lock for {@code stationId}; returns {@code false} when held. */
    public boolean tryAcquire(int stationId) {
        return locks.add(stationId);
    }

    /** Releases the lock for {@code stationId}. No-op when not held. */
    public void release(int stationId) {
        locks.remove(stationId);
    }

    /** Whether a migration is currently in flight for {@code stationId}. */
    public boolean isLocked(int stationId) {
        return locks.contains(stationId);
    }
}
