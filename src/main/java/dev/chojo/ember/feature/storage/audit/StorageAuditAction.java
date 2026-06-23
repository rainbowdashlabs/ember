/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.audit;

/** Discriminator for every row in {@code storage_backend_audit}. */
public enum StorageAuditAction {
    /** A station_storage_config row was created. */
    CREATED,
    /** An existing station_storage_config row was replaced. */
    UPDATED,
    /** A station_storage_config row was deleted. */
    DELETED,
    /** A user-triggered probe succeeded. */
    PROBE_OK,
    /** A user-triggered probe failed. */
    PROBE_FAILED,
    /** A backend migration started. */
    MIGRATION_STARTED,
    /** A backend migration completed successfully. */
    MIGRATION_COMPLETED,
    /** A backend migration failed mid-flight. */
    MIGRATION_FAILED,
    /** A mutation request was refused (e.g. swapping a non-empty backend without migration). */
    REJECTED
}
