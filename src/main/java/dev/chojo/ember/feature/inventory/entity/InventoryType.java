/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * Defines which owners may appear in an inventory. It is never consulted to decide what happens to an
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
    MIXED;

    /**
     * Whether an item of this owner may stand in an inventory of this type.
     *
     * @param owner who would own the item
     * @return true where the type holds gear of that owner
     */
    public boolean holds(ItemOwner owner) {
        return switch (this) {
            case MIXED -> true;
            case INTERNAL -> owner == ItemOwner.STATION;
            case EXTERNAL -> owner == ItemOwner.CLUSTER;
        };
    }
}
