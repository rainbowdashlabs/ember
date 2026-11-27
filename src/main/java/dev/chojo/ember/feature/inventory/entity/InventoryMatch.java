/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import java.util.List;

/**
 * How one inventory answers a search: by itself, through some of its kinds, or through some of its
 * pieces. Kept apart because the lending shop shows a product whenever any of them matches, and shows
 * the kind or piece that matched where the product itself did not.
 *
 * @param inventoryId the inventory
 * @param rank        how well the best of them matched, higher is better
 * @param itself      whether the inventory's own name or description matched
 * @param kinds       the kinds whose name or description matched
 * @param pieces      the pieces whose name or description matched
 */
public record InventoryMatch(int inventoryId, double rank, boolean itself, List<Hit> kinds, List<Hit> pieces) {

    /**
     * A kind or a piece that matched.
     *
     * @param id   the kind's or the piece's id
     * @param name what it is called
     * @param rank how well it matched
     */
    public record Hit(int id, String name, double rank) {}
}
