/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {defaultMovementFilter, filterMovements, inventoryChoices} from './movementFilter'
import type {Movement} from '@/api/movements'

/**
 * The ticks and the search above the queue. They narrow in the browser, because the page already
 * holds every movement of the station, and what they leave is what the table sorts and the export
 * selects from.
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

    return {
        search,
        inventoryIds,
        purposes,
        states,
        turns,
        inventories,
        matching,
    }
}
