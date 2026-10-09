/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ItemCustody, ItemOwner, type ItemChoice} from '@/api/generated/schema'

/** What a piece has to say for itself to be counted, which a full piece and a picker's piece both do. */
type CountedPiece = Pick<ItemChoice, 'custody' | 'ownerKind' | 'artId' | 'inventoryId'>

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
export function isAtHand(item: CountedPiece): boolean {
    if (item.custody === ItemCustody.AT_STATION || item.custody === ItemCustody.WITH_MEMBER) return true
    return item.custody === ItemCustody.WITH_OWNER && item.ownerKind === ItemOwner.STATION
}

/**
 * How many pieces of each kind are at hand.
 *
 * <p>This is the plain count of what exists rather than what is free on one date. A line is
 * written for a whole series of dates, so there is no single date a free count could be taken
 * over. What is free on one date is a different
 * question with a different answer for every date, and it is already answered where it belongs:
 * beside the line, for the date being looked at, with the appointments it collides with named.
 * What belongs in the dialogue is the ceiling that holds on every date, because asking for more
 * pieces than exist is wrong on any of them.
 *
 * @param items every piece the station holds
 * @returns the count per kind, kinds with no piece left out
 */
export function stockByArt(items: CountedPiece[]): Map<number, number> {
    return countBy(items, item => item.artId ?? null)
}

/**
 * How many pieces each inventory holds at hand.
 *
 * @param items every piece the station holds
 * @returns the count per inventory, inventories with no piece left out
 */
export function stockByInventory(items: CountedPiece[]): Map<number, number> {
    return countBy(items, item => item.inventoryId)
}

function countBy(items: CountedPiece[], keyOf: (item: CountedPiece) => number | null): Map<number, number> {
    const counts = new Map<number, number>()
    for (const item of items) {
        if (!isAtHand(item)) continue
        const key = keyOf(item)
        if (key == null) continue
        counts.set(key, (counts.get(key) ?? 0) + 1)
    }
    return counts
}
