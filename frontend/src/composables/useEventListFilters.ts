/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import {EventStates, type EventStateName} from '@/api/events'
import {useRouteQueryRef} from '@/composables/useRouteQueryRef'

const SEARCH_DEBOUNCE_MS = 250

/**
 * What a list of appointments is narrowed to: the tab, the search and the two dates.
 *
 * <p>All of it lives in the address. A reader who opened the past tab, searched it and cut it down
 * to last autumn can copy what they are looking at, and the four narrowings are independent of one
 * another, so a search on the past tab searches the past.
 *
 * <p>Typing is debounced before it reaches either the address or the server. The box is a ref of its
 * own for that reason, and follows the address back whenever the reader arrives at a different one.
 *
 * @param onChange runs once whenever any of the filters settles on a new value
 */
export function useEventListFilters(onChange: () => void) {
    const tab = useRouteQueryRef('tab', EventStates.CURRENT)
    const search = useRouteQueryRef('search')
    const categoryId = useRouteQueryRef('category')
    const from = useRouteQueryRef('from')
    const to = useRouteQueryRef('to')

    const state = computed<EventStateName>(() =>
        tab.value === EventStates.PAST ? EventStates.PAST : EventStates.CURRENT)
    const isPast = computed(() => state.value === EventStates.PAST)

    const searchInput = ref(search.value)
    let debounce: ReturnType<typeof setTimeout> | null = null

    watch(searchInput, value => {
        if (debounce) clearTimeout(debounce)
        debounce = setTimeout(() => {
            if (search.value !== value) search.value = value
        }, SEARCH_DEBOUNCE_MS)
    })

    watch(search, value => {
        if (searchInput.value !== value) searchInput.value = value
    })

    watch([state, search, categoryId, from, to], () => onChange())

    return {tab, state, isPast, search, searchInput, categoryId, from, to}
}
