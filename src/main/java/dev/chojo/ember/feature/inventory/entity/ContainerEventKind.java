/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Lifecycle events recorded against a container in {@code inventory_container_history}.
 */
public enum ContainerEventKind {
    /**
     * Container row was created.
     */
    CREATED,
    /**
     * Container was renamed.
     */
    RENAMED,
    /**
     * Container's parent changed.
     */
    MOVED,
    /**
     * Container was deleted; row survives with {@code container_id = NULL}.
     */
    DELETED
}
