/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The list with one entry taken out and put back in at another place.
 *
 * <p>What every sortable list does when a row is moved, whether it was dragged or walked up with the
 * arrows: taken out first and inserted after, so the entries in between close the gap rather than one
 * of them being overwritten. The list handed in is left alone.
 *
 * @param items the current order
 * @param fromIndex where the entry is now
 * @param toIndex where it belongs
 * @return the new order, or the old one where either place is not in the list
 */
export function moveWithin<T>(items: readonly T[], fromIndex: number, toIndex: number): T[] {
    const next = [...items]
    if (toIndex < 0 || toIndex >= next.length) return next
    const [moved] = next.splice(fromIndex, 1)
    if (moved === undefined) return [...items]
    next.splice(toIndex, 0, moved)
    return next
}
