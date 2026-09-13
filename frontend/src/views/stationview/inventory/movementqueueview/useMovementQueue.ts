/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useSortable, type SortDirection} from '@/composables/useSortable'
import {
    defaultMovementFilter,
    filterMovements,
    inventoryChoices,
    movementComparators,
    naturalDirection,
    turnRank,
    type MovementSortKey,
} from './movementFilter'
import type {Movement} from '@/api/movements'

/**
 * Narrowing and ordering of the queue. Both happen in the browser, because the page already holds
 * every movement of the station, and both feed the same list the export selects from.
 *
 * <p>The default order is by whose turn it is rather than by date: the rows somebody can act on stand
 * at the top, and the date orders within each group.
 */
export function useMovementQueue(movements: () => Movement[]) {
    const search = ref(defaultMovementFilter.search)
    const inventoryIds = ref([...defaultMovementFilter.inventoryIds])
    const purposes = ref<string[]>([...defaultMovementFilter.purposes])
    const states = ref<string[]>([...defaultMovementFilter.states])
    const turns = ref<string[]>([...defaultMovementFilter.turns])

    const inventories = computed(() => inventoryChoices(movements()))

    const matching = computed(() => filterMovements(movements(), {
        search: search.value,
        inventoryIds: inventoryIds.value,
        purposes: purposes.value,
        states: states.value,
        turns: turns.value,
    }))

    const sortKey = ref<MovementSortKey>('turn')
    const direction = ref<SortDirection>(naturalDirection('turn'))

    const {sorted, toggle} = useSortable<Movement, MovementSortKey>({
        items: matching,
        comparators: movementComparators,
        initialKey: 'turn',
        state: {key: sortKey, direction},
    })

    /**
     * The rows in the order the page draws them: the chosen column, and within an equal turn the
     * oldest first, because a movement nobody has touched for a fortnight is the one to deal with.
     */
    const visible = computed(() => {
        if (sortKey.value !== 'turn') return sorted.value
        return [...sorted.value].sort((a, b) => {
            const byTurn = turnRank(a) - turnRank(b)
            if (byTurn !== 0) return direction.value === 'asc' ? byTurn : -byTurn
            return a.createdAt.localeCompare(b.createdAt)
        })
    })

    /** Picks a column without flipping it, which is what a list of choices rather than a header does. */
    function selectSort(key: MovementSortKey) {
        sortKey.value = key
        direction.value = naturalDirection(key)
    }

    return {
        search,
        inventoryIds,
        purposes,
        states,
        turns,
        inventories,
        visible,
        sortKey,
        direction,
        toggleSort: toggle,
        selectSort,
    }
}
