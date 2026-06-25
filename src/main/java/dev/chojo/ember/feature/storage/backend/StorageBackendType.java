/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.backend;

/**
 * Discriminator for the kind of {@link StorageBackend} implementation. Following the project
 * convention "prefer a new enum variant over a boolean flag" — adding a backend means adding
 * an enum entry here, never a config flag.
 */
public enum StorageBackendType {
    LOCAL,
    SMB,
    SFTP,
    S3
}
