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
    CREATED(ContainerHistoryDetails.Created.class),
    /**
     * Container was renamed.
     */
    RENAMED(ContainerHistoryDetails.Renamed.class),
    /**
     * Container's parent changed.
     */
    MOVED(ContainerHistoryDetails.Moved.class),
    /**
     * Container was deleted; row survives with {@code container_id = NULL}.
     */
    DELETED(ContainerHistoryDetails.Deleted.class);

    private final Class<? extends ContainerHistoryDetails> detailsClass;

    ContainerEventKind(Class<? extends ContainerHistoryDetails> detailsClass) {
        this.detailsClass = detailsClass;
    }

    /**
     * Returns the {@link ContainerHistoryDetails} variant that the {@code details}
     * JSONB of a row with this kind deserialises into.
     */
    public Class<? extends ContainerHistoryDetails> detailsClass() {
        return detailsClass;
    }
}
