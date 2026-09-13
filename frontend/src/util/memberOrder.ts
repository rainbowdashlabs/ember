/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** Names sort the way a German reader expects, and neither case nor an accent separates two of them. */
const collator = new Intl.Collator('de', {sensitivity: 'base'})

/**
 * Two members' names, in the order every member menu lists them.
 *
 * <p>A display name is the first name and the surname joined, so comparing the whole string compares the
 * first name first and reaches the surname only where two people share one. That is what somebody hunting
 * through a menu scans by: they know the person as Anna, not as Krüger.
 *
 * <p>Somebody with no name at all sorts last rather than first, so an incomplete row never sits above the
 * people who were actually being looked for.
 *
 * <p>The big member list is deliberately not covered by this. It is a table with sortable columns and a
 * sort each reader keeps across sessions, and somebody who has sorted a table has asked for an order.
 */
export function compareMemberNames(one: string, other: string): number {
    const left = one.trim()
    const right = other.trim()
    if (!left || !right) return left ? -1 : right ? 1 : 0
    return collator.compare(left, right)
}

/**
 * The same order, over whatever a list happens to hold.
 *
 * @param nameOf the display name of a row
 * @returns a comparator to hand to {@code toSorted}
 */
export function byMemberName<T>(nameOf: (entry: T) => string): (one: T, other: T) => number {
    return (one, other) => compareMemberNames(nameOf(one), nameOf(other))
}
