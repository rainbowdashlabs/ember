/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

/** The kind of {@link StorageBackend}. */
public enum StorageBackendType {
    LOCAL,
    SMB,
    SFTP,
    S3
}
