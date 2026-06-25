/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Discriminator for the kind of subject an {@link InventoryCheck} covers.
 */
public enum InventoryCheckScope {
    /**
     * Check is scoped to a single station member and covers their assigned items.
     */
    MEMBER,
    /**
     * Check is scoped to a storage container and covers the items physically held inside.
     */
    CONTAINER
}
