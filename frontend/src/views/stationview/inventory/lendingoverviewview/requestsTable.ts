/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {LendingStatus, type LendingRequestResponse} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import {byValue} from '@/composables/useSortable'
import {useDataTable} from '@/composables/useDataTable'

/** Which way a request goes: for the station's own gear, or for a partner's. */
export type RequestDirection = 'incoming' | 'outgoing'

export function directionOf(entry: LendingRequestResponse): RequestDirection {
    return entry.isOwner ? 'incoming' : 'outgoing'
}

/** Every request in both directions as one table, the newest asked first, filterable by its columns. */
export function useRequestsTable(rows: Ref<LendingRequestResponse[]>) {
    const {t} = useI18n()
    const columns = computed<TableColumn<LendingRequestResponse>[]>(() => [
        {
            key: 'station', label: t('lendingOverview.colStation'), type: ColumnTypes.TEXT, pinned: true,
            value: row => row.isOwner ? row.requestingStationName : row.owningStationName,
        },
        {
            key: 'direction', label: t('lendingOverview.colDirection'), type: ColumnTypes.ENUM, value: directionOf,
            options: enumOptions(['incoming', 'outgoing'], value => t(`lendingOverview.direction.${value}`)),
        },
        {key: 'asked', label: t('lendingOverview.colAsked'), type: ColumnTypes.DATE, value: row => row.request.createdAt},
        {key: 'from', label: t('lending.dateFrom'), type: ColumnTypes.DATE, value: row => row.request.requestedDateFrom},
        {key: 'to', label: t('lending.dateTo'), type: ColumnTypes.DATE, value: row => row.request.requestedDateTo},
        {key: 'lines', label: t('lendingOverview.colLines'), type: ColumnTypes.TEXT, value: row => row.itemSummary},
        {
            key: 'status', label: t('lendingOverview.colStatus'), type: ColumnTypes.ENUM, value: row => row.request.status,
            options: enumOptions(Object.values(LendingStatus), value => t(`lending.status.${value}`)),
        },
    ])
    return useDataTable<LendingRequestResponse>({
        id: 'lending-requests',
        rows: () => rows.value,
        columns,
        rowKey: row => row.request.id,
        fallbackSort: byValue(row => row.request.createdAt),
        sort: {key: 'asked', direction: 'desc'},
    })
}
