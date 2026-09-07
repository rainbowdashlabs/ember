/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useSortable, type SortDirection} from '@/composables/useSortable'
import {
    defaultExchangeFilter,
    exchangeComparators,
    filterExchanges,
    inventoryChoices,
    naturalDirection,
    type ExchangeSortKey,
} from './exchangeFilter'
import type {ExchangeRequestEntry} from '@/api/exchanges'

/**
 * Narrowing and ordering of the exchange list. Both happen in the browser, because the page already
 * holds every exchange of the station, and both feed the same list the export selects from.
 */
export function useExchangeTable(requests: () => ExchangeRequestEntry[]) {
    const search = ref(defaultExchangeFilter.search)
    const inventoryIds = ref([...defaultExchangeFilter.inventoryIds])
    const statuses = ref<string[]>([...defaultExchangeFilter.statuses])

    const inventories = computed(() => inventoryChoices(requests()))

    const matching = computed(() => filterExchanges(requests(), {
        search: search.value,
        inventoryIds: inventoryIds.value,
        statuses: statuses.value,
    }))

    const sortKey = ref<ExchangeSortKey>('date')
    const direction = ref<SortDirection>(naturalDirection('date'))

    const {sorted, toggle} = useSortable<ExchangeRequestEntry, ExchangeSortKey>({
        items: matching,
        comparators: exchangeComparators,
        initialKey: 'date',
        state: {key: sortKey, direction},
    })

    /** Picks a column without flipping it, which is what a list of choices rather than a header does. */
    function selectSort(key: ExchangeSortKey) {
        sortKey.value = key
        direction.value = naturalDirection(key)
    }

    return {
        search,
        inventoryIds,
        statuses,
        inventories,
        visible: sorted,
        sortKey,
        direction,
        toggleSort: toggle,
        selectSort,
    }
}
