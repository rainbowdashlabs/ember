/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Says which owners may appear in an inventory. It is never consulted to decide what happens to an
 * item: what happens to an item follows from that item's own {@link ItemOwner}.
 */
public enum InventoryType {
    /**
     * Only items the station owns itself.
     */
    INTERNAL,
    /**
     * Only items the body above the station owns.
     */
    EXTERNAL,
    /**
     * Both owners may appear side by side.
     */
    MIXED
}
