/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

/**
 * Indicates the ownership scope of a table or file store.
 */
public enum Scope {
    /** Belongs to a single station. Included in station export/import. */
    STATION,

    /** Instance-level configuration shared across all stations. */
    INSTANCE,

    /** Per-user data tied to an account or member rather than a station. */
    USER
}
