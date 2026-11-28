/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, shallowRef} from 'vue'

/**
 * Which pieces of one inventory are ticked for a change to all of them at once.
 *
 * <p>The selection belongs to the screen, not to a table, so it spans every block of a drawer sorted
 * into kinds and survives the list being read again after a change. Pieces that are gone after such
 * a read are dropped from it by {@link ItemSelection.keepOnly}.
 */
export function useItemSelection() {
    const active = ref(false)
    const ids = shallowRef<ReadonlySet<number>>(new Set())

    const count = computed(() => ids.value.size)

    function has(id: number): boolean {
        return ids.value.has(id)
    }

    function toggle(id: number) {
        const next = new Set(ids.value)
        if (!next.delete(id)) next.add(id)
        ids.value = next
    }

    /** Whether every one of these pieces is ticked, which is false for none at all. */
    function allOf(pieceIds: readonly number[]): boolean {
        return pieceIds.length > 0 && pieceIds.every(id => ids.value.has(id))
    }

    /** Whether some but not all of these pieces are ticked, for a box standing for all of them. */
    function someOf(pieceIds: readonly number[]): boolean {
        return !allOf(pieceIds) && pieceIds.some(id => ids.value.has(id))
    }

    /** Ticks all of these pieces, or unticks them where all of them already are. */
    function toggleAll(pieceIds: readonly number[]) {
        const next = new Set(ids.value)
        if (allOf(pieceIds)) pieceIds.forEach(id => next.delete(id))
        else pieceIds.forEach(id => next.add(id))
        ids.value = next
    }

    /** Ticks all of these pieces, keeping the ones already ticked. */
    function selectAll(pieceIds: readonly number[]) {
        ids.value = new Set([...ids.value, ...pieceIds])
    }

    /** Drops every ticked piece that is not among these, such as one deleted meanwhile. */
    function keepOnly(pieceIds: readonly number[]) {
        const present = new Set(pieceIds)
        if ([...ids.value].every(id => present.has(id))) return
        ids.value = new Set([...ids.value].filter(id => present.has(id)))
    }

    function clear() {
        ids.value = new Set()
    }

    /** Switches the mode on or off. Switching it off forgets what was ticked. */
    function setActive(on: boolean) {
        active.value = on
        if (!on) clear()
    }

    const selectedIds = computed(() => [...ids.value])

    return {active, count, selectedIds, has, toggle, allOf, someOf, toggleAll, selectAll, keepOnly, clear, setActive}
}

export type ItemSelection = ReturnType<typeof useItemSelection>
