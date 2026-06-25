/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

/**
 * Optional features a {@link StorageBackend} may declare. The resolver refuses to bind a
 * category that demands a capability the backend does not advertise — misconfiguration fails
 * at probe time, not at first read.
 */
public enum BackendCapability {
    /**
     * Backend can record and retrieve a per-key last-access timestamp. Required by categories
     * with the {@code accessTimeLru} flag (e.g. the map tile cache).
     */
    ACCESS_TIME_TRACKING,

    /**
     * Backend honours the per-category {@code posixMode} flag and applies the matching POSIX
     * permission bits after every write. Required by categories that store sensitive material
     * such as the discovery key.
     */
    POSIX_MODE
}
