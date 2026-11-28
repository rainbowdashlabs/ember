/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useRouteQueryRef} from '@/composables/useRouteQueryRef'

/** The orders the shop offers: the partners' own (best match or nearest first) or by name. */
export type ShopSort = 'best' | 'name'

/**
 * What the shop is narrowed to, kept in the address so a link to "radios free on the 12th" can be
 * shared and the back button returns to the same shop. The partner station goes under `partner`,
 * because `station` names the tab's own station on every station address.
 */
export function useShopQuery() {
    const dateFrom = useRouteQueryRef('from')
    const dateTo = useRouteQueryRef('to')
    const search = useRouteQueryRef('q')
    const station = useRouteQueryRef('partner')
    const sortRaw = useRouteQueryRef('sort', 'best')
    const sort = computed<ShopSort>({
        get: () => (sortRaw.value === 'name' ? 'name' : 'best'),
        set: value => { sortRaw.value = value },
    })
    return {dateFrom, dateTo, search, station, sort}
}
