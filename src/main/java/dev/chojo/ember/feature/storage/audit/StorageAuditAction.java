/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.audit;

/** What a row of the storage backend audit records. */
public enum StorageAuditAction {
    /** A station's or an association's own storage was set. */
    CREATED,
    /** A station's or an association's own storage was replaced, or took new credentials. */
    UPDATED,
    /** A station's or an association's own storage was given up. */
    DELETED,
    /** A probe a user started succeeded. */
    PROBE_OK,
    /** A probe a user started failed. */
    PROBE_FAILED,
    MIGRATION_STARTED,
    MIGRATION_COMPLETED,
    MIGRATION_FAILED,
    /** A change was refused, such as swapping a backend that still holds bytes without moving them. */
    REJECTED,
    /** The instance default was changed in the admin panel without moving bytes. */
    INSTANCE_DEFAULT_UPDATED,
    INSTANCE_MIGRATION_STARTED,
    INSTANCE_MIGRATION_COMPLETED,
    /** An instance-wide move failed; the previous backend stays in use. */
    INSTANCE_MIGRATION_FAILED,
    /** An association changed how far its storage reaches or whether its stations may choose their own. */
    POLICY_CHANGED
}
