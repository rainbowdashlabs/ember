/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {watch, type Ref} from 'vue'
import {useRoute} from 'vue-router'
import type {DataTableApi} from '@/composables/useDataTable'
import {stateToken} from '@/util/dateFilter'
import {FILTERABLE_EXPIRY_STATES, type ExpiryStateName} from '@/util/expiry'

/**
 * The column and the states an address asks the member list to open on, such as
 * {@code ?field=12&state=expiring,expired}.
 */
export interface AddressFilter {
    field: string
    states: ExpiryStateName[]
}

/** Reads the filter an address names, or nothing where it names no field or no state this list knows. */
export function addressFilterOf(query: Record<string, unknown>): AddressFilter | null {
    const field = typeof query.field === 'string' ? query.field.trim() : ''
    const named = typeof query.state === 'string' ? query.state.split(',') : []
    const states = FILTERABLE_EXPIRY_STATES.filter(state => named.some(word => word.trim().toUpperCase() === state))
    return field && states.length > 0 ? {field, states} : null
}

/**
 * Opens the member list filtered the way its address says, once the people and their questions have
 * first loaded: all members, the named column shown, and its filter holding the named states.
 *
 * <p>This is where member management's reminder about expiry dates leads, so that one press shows
 * whose date runs out rather than the whole list.
 *
 * @param loading whether the list is still loading, since a column does not exist before its question
 * @param activeTab the kind of member the list shows, which the filter widens to everybody
 * @param table the table the filter stands on
 */
export function useAddressFilter<Row>(loading: Ref<boolean>, activeTab: Ref<string>, table: DataTableApi<Row>) {
    const route = useRoute()
    const filter = addressFilterOf(route.query)
    if (!filter) return
    let applied = false
    watch(loading, (isLoading, wasLoading) => {
        if (isLoading || !wasLoading || applied) return
        applied = true
        activeTab.value = 'ALL'
        table.setColumnsVisible([filter.field], true)
        table.setFilter(filter.field, new Set(filter.states.map(stateToken)), false)
    })
}
