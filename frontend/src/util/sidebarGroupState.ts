/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onMounted, onUnmounted, watch, type Ref} from 'vue'
import {browserRef, browserShallowRef} from '@/util/browserState'

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
const claims = browserRef(new Map<number, number>())

const handles = browserShallowRef({next: 0})

function report(id: number, length: number): void {
    const next = new Map(claims.value)
    if (length > 0) next.set(id, length)
    else next.delete(id)
    claims.value = next
}

function release(id: number): void {
    const next = new Map(claims.value)
    next.delete(id)
    claims.value = next
}

/**
 * Enters a group in the register for as long as it is mounted, reporting how well it matches the
 * page being shown.
 *
 * <p>From the mount on, never while the group sets up. A server render never mounts, so nothing it
 * draws is registered, and the browser's first render sees the same empty register the server saw,
 * which is what keeps the highlight the same in both.
 *
 * @param matchLength the length of the group's longest matching prefix, or 0 when none matches
 */
export function followSidebarMatch(matchLength: Readonly<Ref<number>>): void {
    let id = 0
    onMounted(() => {
        id = ++handles.value.next
        watch(matchLength, length => report(id, length), {immediate: true})
    })
    onUnmounted(() => release(id))
}

/** The best match anybody has claimed, which is the only one that lights up; 0 while nobody has. */
export function bestSidebarMatch(): number {
    let best = 0
    for (const length of claims.value.values()) best = Math.max(best, length)
    return best
}
