/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ItemCustody, ItemOwner, type InventoryItem} from '@/api/inventory'

/**
 * Whether a piece is one the station could actually bring along.
 *
 * <p>Wider than free stock and narrower than everything the station holds, which is the same line
 * the backend draws when it counts what a line of an appointment asks for. Gear permanently handed
 * to a group leader counts, because that is the ordinary state of a radio rather than an exception.
 * Gear that is lost, in the post or with a partner does not, because it is somewhere else.
 *
 * @param item the piece
 * @returns whether it is at hand
 */
export function isAtHand(item: InventoryItem): boolean {
    if (item.custody === ItemCustody.AT_STATION || item.custody === ItemCustody.WITH_MEMBER) return true
    return item.custody === ItemCustody.WITH_OWNER && item.ownerKind === ItemOwner.STATION
}

/**
 * How many pieces of each kind are at hand.
 *
 * <p>This is the plain count of what exists rather than what is free on one evening. A line is
 * written either for a whole series of evenings or, in a collection, for no date at all, so there is
 * no single evening a free count could be taken over. What is free on one evening is a different
 * question with a different answer for every evening, and it is already answered where it belongs:
 * beside the line, for the evening being looked at, with the appointments it collides with named.
 * What belongs in the dialogue is the ceiling that holds on every evening, because asking for more
 * pieces than exist is wrong whatever the date.
 *
 * @param items every piece the station holds
 * @returns the count per kind, kinds with no piece left out
 */
export function stockByArt(items: InventoryItem[]): Map<number, number> {
    return countBy(items, item => item.artId ?? null)
}

/**
 * How many pieces each inventory holds at hand.
 *
 * @param items every piece the station holds
 * @returns the count per inventory, inventories with no piece left out
 */
export function stockByInventory(items: InventoryItem[]): Map<number, number> {
    return countBy(items, item => item.inventoryId)
}

function countBy(items: InventoryItem[], keyOf: (item: InventoryItem) => number | null): Map<number, number> {
    const counts = new Map<number, number>()
    for (const item of items) {
        if (!isAtHand(item)) continue
        const key = keyOf(item)
        if (key == null) continue
        counts.set(key, (counts.get(key) ?? 0) + 1)
    }
    return counts
}
