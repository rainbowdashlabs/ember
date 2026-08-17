/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * In-memory flag flipped on for the duration of an instance-wide storage backend migration.
 * When the flag is set every write through {@link StorageService} is refused with a {@code 503}
 * so the byte-copy loop and the resolver flip do not race with concurrent uploads.
 *
 * <p>The state lives on its own singleton instead of on the migration service so
 * {@link StorageService} can consult it without introducing a circular dependency with the
 * migration service (which itself depends on {@code StorageService} to walk and write bytes).
 * A JVM restart clears the flag - correct because no migration is actively running and the
 * previous backend remains authoritative.
 */
@Singleton
public class InstanceStorageReadOnlyState {
    private static final Logger log = LoggerFactory.getLogger(InstanceStorageReadOnlyState.class);

    private final AtomicBoolean readOnly = new AtomicBoolean(false);

    /**
     * Marks the instance as read-only for an in-flight migration. Idempotent; a duplicate
     * call returns {@code false} so the caller can fail fast.
     */
    public boolean lock() {
        boolean acquired = readOnly.compareAndSet(false, true);
        if (acquired) {
            log.info("Instance storage locked read-only for migration");
        } else {
            log.warn("Instance storage already locked read-only; lock acquisition refused");
        }
        return acquired;
    }

    /**
     * Clears the read-only flag. Idempotent.
     */
    public void unlock() {
        readOnly.set(false);
        log.info("Instance storage read-only lock cleared");
    }

    /**
     * Whether the instance is currently flagged read-only for an in-flight migration.
     */
    public boolean isLocked() {
        return readOnly.get();
    }
}
