/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useRouteQueryRef} from '@/composables/useRouteQueryRef'
import {withoutKey} from '@/util/record'

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
    const inCartRaw = useRouteQueryRef('incart')
    const onlyInCart = computed<boolean>({
        get: () => inCartRaw.value === '1',
        set: value => { inCartRaw.value = value ? '1' : '' },
    })
    const sort = computed<ShopSort>({
        get: () => (sortRaw.value === 'name' ? 'name' : 'best'),
        set: value => { sortRaw.value = value },
    })
    const route = useRoute()
    const router = useRouter()

    /**
     * Sets both days in one step. Two writes of one tick would each start from the address as it was
     * and the second would undo the first.
     */
    function setDays(from: string, to: string) {
        const others = withoutKey(withoutKey(route.query, 'from'), 'to')
        void router.replace({query: {...others, ...(from ? {from} : {}), ...(to ? {to} : {})}})
    }

    return {dateFrom, dateTo, search, station, sort, onlyInCart, setDays}
}
