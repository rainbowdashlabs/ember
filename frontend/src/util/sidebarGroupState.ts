/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'

/**
 * Which sidebar group is the one you are standing in.
 *
 * <p>A group cannot answer that alone. Several match at once whenever one group's prefix is a prefix of
 * another's, and the association's first group is declared `/cluster`, which every cluster route begins
 * with: it was therefore lit on every page of the panel, which is the one group that says nothing about
 * where you are. The rule that settles it is the ordinary one a router uses, longest match wins, and
 * applying it needs the groups to know about each other.
 *
 * <p>Module level rather than provided, because more than one sidebar is mounted at a time (the desktop
 * rail and the flyout) and both would answer the same question the same way. Every group claims the
 * length of its longest matching prefix and reads back whether anybody claimed more.
 */
const claims = ref(new Map<number, number>())

let nextId = 0

/** A group's own handle in the register, held for as long as it is mounted. */
export function claimSidebarGroup(): number {
    nextId += 1
    return nextId
}

/**
 * Says how well this group matches the page being shown.
 *
 * @param id     the group's handle
 * @param length the length of its longest matching prefix, or 0 when none matches
 */
export function reportSidebarMatch(id: number, length: number): void {
    const next = new Map(claims.value)
    if (length > 0) next.set(id, length)
    else next.delete(id)
    claims.value = next
}

/** Forgets a group that has gone away, so an unmounted sidebar keeps nothing lit. */
export function releaseSidebarGroup(id: number): void {
    const next = new Map(claims.value)
    next.delete(id)
    claims.value = next
}

/** The best match anybody has claimed, which is the only one that lights up. */
export const bestSidebarMatch = computed(() => {
    let best = 0
    for (const length of claims.value.values()) best = Math.max(best, length)
    return best
})
