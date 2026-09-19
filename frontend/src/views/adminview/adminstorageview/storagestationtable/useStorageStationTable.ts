/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type MaybeRefOrGetter} from 'vue'
import {useI18n} from 'vue-i18n'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import {emptyTableState, useDataTable} from '@/composables/useDataTable'
import {useStorageCapabilities, type StorageRoomRow} from '@/composables/useStorageQuotas'
import {formatBytes} from '@/util/storage'
import {StorageStatus, storageStatusOf} from './storageStatus'

/**
 * Every station's room as a table, the fullest first: its name, what it keeps, how much of its
 * quota that is, the state that puts it in, and the preset or rule its quota comes from.
 *
 * <p>A station on a backend of its own has no quota here, so its quota, state and preset stay
 * empty and sort last.
 */
export function useStorageStationTable(stations: MaybeRefOrGetter<readonly StorageRoomRow[]>) {
    const {t} = useI18n()
    const capabilities = useStorageCapabilities()

    function presetOf(station: StorageRoomRow): string | null {
        if (station.usesOwnBackend) return null
        if (station.presetName) return station.presetName
        if (capabilities.showsOrigin && station.origin) return t(`storageMonitoring.origin.${station.origin}`)
        return t('storageMonitoring.defaultQuota')
    }

    function quotaOf(station: StorageRoomRow): string {
        if (station.usesOwnBackend) return '-'
        return `${station.quotaUsedPercent}% / ${formatBytes(station.quotaBytes)}`
    }

    const columns = computed<TableColumn<StorageRoomRow>[]>(() => [
        {key: 'name', label: t('storageMonitoring.stationName'), type: ColumnTypes.TEXT, value: station => station.stationName, pinned: true},
        {
            key: 'usage', label: t('storageMonitoring.usage'), type: ColumnTypes.NUMBER,
            value: station => station.totalBytes, display: station => formatBytes(station.totalBytes),
        },
        {
            key: 'percent', label: t('storageMonitoring.quota'), type: ColumnTypes.NUMBER, align: 'right',
            value: station => station.usesOwnBackend ? null : station.quotaUsedPercent, display: quotaOf,
        },
        {
            key: 'status', label: t('storageMonitoring.status'), type: ColumnTypes.ENUM, align: 'center',
            value: storageStatusOf,
            options: [
                {value: StorageStatus.OK, label: t('storageMonitoring.ok')},
                {value: StorageStatus.WARNING, label: t('storageMonitoring.warning')},
                {value: StorageStatus.FULL, label: t('storageMonitoring.full')},
            ],
        },
        {key: 'preset', label: t('storageMonitoring.preset'), type: ColumnTypes.TEXT, value: presetOf},
    ])

    return useDataTable<StorageRoomRow>({
        id: 'storage-stations',
        rows: stations,
        columns,
        rowKey: station => station.stationId,
        state: ref(emptyTableState('percent', 'desc')),
    })
}
