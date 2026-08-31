/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import java.util.Map;

/**
 * What one station offers one partner, resolved into a form that answers a single piece of gear
 * without going back to the database.
 *
 * <p>The narrowest row that exists decides. An item's own row beats its inventory's, whether it
 * grants or withholds, and a row that exists but names other partners is still the narrowest row:
 * "the trailer goes to A but not to B" is written as a grant to A on the trailer, and B is told no
 * by that same row rather than by the inventory above it.
 *
 * <p>Above all of it sits the partnership itself. A partner the station has turned lending off for
 * is offered nothing, whatever any row says.
 *
 * @param lendingEnabled whether the partnership still permits lending at all
 * @param byInventory    per inventory, whether the row that exists grants to this partner
 * @param byItem         per item, whether the row that exists grants to this partner
 */
public record SharePolicy(boolean lendingEnabled, Map<Integer, Boolean> byInventory, Map<Integer, Boolean> byItem) {

    /** Offers nothing at all, which is what a station that is not a partner gets. */
    public static SharePolicy closed() {
        return new SharePolicy(false, Map.of(), Map.of());
    }

    /**
     * Whether one piece of gear is on offer to this partner.
     *
     * @param inventoryId the inventory holding it
     * @param itemId      the item itself
     */
    public boolean allows(int inventoryId, int itemId) {
        if (!lendingEnabled) return false;
        Boolean forItem = byItem.get(itemId);
        if (forItem != null) return forItem;
        return Boolean.TRUE.equals(byInventory.get(inventoryId));
    }

    /**
     * Whether the whole inventory is on offer, which is the answer for a listing that has no single
     * item in hand yet.
     */
    public boolean allowsInventory(int inventoryId) {
        return lendingEnabled && Boolean.TRUE.equals(byInventory.get(inventoryId));
    }

    /**
     * Whether this partner is offered anything at all. It separates the two empty answers a browse
     * screen may give, and says nothing finer than that on purpose.
     */
    public boolean offersAnything() {
        if (!lendingEnabled) return false;
        return byInventory.containsValue(Boolean.TRUE) || byItem.containsValue(Boolean.TRUE);
    }
}
