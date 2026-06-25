/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.migration;

import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Set;

/**
 * Migration mutex that brokers two flavours of work against the same lock space:
 * per-station migrations and instance-wide migrations. Per-station migrations remain
 * independent of each other so two stations may still migrate in parallel, but an
 * instance-wide swap is mutually exclusive with every per-station migration —
 * no instance migration can start while any station lock is held, and no station migration
 * can start while the instance lock is held.
 *
 * <p>The registry is in-memory by design — restarting Ember drops every lock. A re-run after
 * a restart walks the source again, skips keys already present on the target via
 * {@code exists()} + SHA-256, and finishes the job.
 */
@Singleton
public class MigrationLockRegistry {
    private final Object lock = new Object();
    private final Set<Integer> stationLocks = new HashSet<>();
    private boolean instanceLocked = false;

    /**
     * Attempts to acquire the lock for {@code stationId}. Fails when an instance-wide migration
     * is in flight or when the same station is already locked.
     */
    public boolean tryAcquire(int stationId) {
        synchronized (lock) {
            if (instanceLocked) return false;
            return stationLocks.add(stationId);
        }
    }

    /**
     * Releases the lock for {@code stationId}. No-op when not held.
     */
    public void release(int stationId) {
        synchronized (lock) {
            stationLocks.remove(stationId);
        }
    }

    /**
     * Whether a per-station migration is currently in flight for {@code stationId}.
     */
    public boolean isLocked(int stationId) {
        synchronized (lock) {
            return stationLocks.contains(stationId);
        }
    }

    /**
     * Attempts to acquire the instance-wide migration lock. Fails when the instance lock is
     * already held or any per-station lock is held.
     */
    public boolean tryAcquireInstance() {
        synchronized (lock) {
            if (instanceLocked) return false;
            if (!stationLocks.isEmpty()) return false;
            instanceLocked = true;
            return true;
        }
    }

    /**
     * Releases the instance-wide migration lock. No-op when not held.
     */
    public void releaseInstance() {
        synchronized (lock) {
            instanceLocked = false;
        }
    }

    /**
     * Whether an instance-wide migration is currently in flight.
     */
    public boolean isInstanceLocked() {
        synchronized (lock) {
            return instanceLocked;
        }
    }
}
