/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

/**
 * Who pays for the storage a station writes to, and therefore who has a say in how much of it there is.
 *
 * <p>This is not the same question as whose numbers apply. A station under a cluster is always governed by
 * what its cluster granted, whoever pays. What the payer decides is what stands behind that: the instance's
 * own defaults when the instance pays, and nothing at all when it does not.
 */
public enum QuotaAuthority {
    /**
     * Nobody. The station brings its own storage backend and pays for it, so neither the instance nor the
     * cluster above it has any business limiting what it keeps.
     */
    NOBODY,
    /**
     * The cluster, which brings the storage its stations write to. What it granted is the whole of the limit,
     * and a dimension it said nothing about is unbounded.
     */
    CLUSTER,
    /**
     * The instance, which is the answer whenever nobody below it brought storage of their own. Its configured
     * defaults stand behind whatever a cluster granted.
     */
    INSTANCE
}
