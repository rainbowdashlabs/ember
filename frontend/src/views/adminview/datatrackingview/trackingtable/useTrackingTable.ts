/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {DataTracking, TableEntry} from '@/api/dataTracking'
import {useDataTable} from '@/composables/useDataTable'
import {TRACKING_CONTEXTS, trackingColumns} from './trackingColumns'
import {searchTextOf, trackingRowsOf, type TrackingRow} from './trackingRow'

export type FilterContext = 'all' | 'transfer' | 'gdprExport' | 'gdprDeletion'
export type FilterStatus = 'all' | 'TRACKED' | 'IGNORED' | 'UNVERIFIED' | 'NEEDS_REVIEW'

/**
 * The tracking inventory as a table, narrowed first by the context and status toggles above it.
 *
 * <p>A status with every context chosen keeps a table where any of its three contexts stands so;
 * "needs review" keeps those where anything, a context or a column, still waits to be verified.
 */
export function useTrackingTable(tracking: Ref<DataTracking | null>) {
    const {t} = useI18n()

    const filterContext = ref<FilterContext>('all')
    const filterStatus = ref<FilterStatus>('all')

    const rows = computed(() => trackingRowsOf(tracking.value))

    function statusesIn(entry: TableEntry): string[] {
        const context = filterContext.value
        const contexts = context === 'all' ? Object.values(TRACKING_CONTEXTS) : [TRACKING_CONTEXTS[context]!]
        return contexts.map(candidate => candidate.statusOf(entry))
    }

    function passesToggles(row: TrackingRow): boolean {
        if (filterStatus.value === 'all') return true
        if (filterStatus.value === 'NEEDS_REVIEW') return row.needsReview
        return statusesIn(row.entry).includes(filterStatus.value)
    }

    const table = useDataTable<TrackingRow>({
        id: 'admin-data-tracking',
        perStation: false,
        rows: computed(() => rows.value.filter(passesToggles)),
        columns: computed(() => trackingColumns(t)),
        rowKey: row => row.name,
        fallbackSort: (a, b) => a.name.localeCompare(b.name),
        searchText: searchTextOf,
    })

    const needsReviewCount = computed(() => rows.value.filter(row => row.needsReview).length)

    return {table, filterContext, filterStatus, needsReviewCount}
}
