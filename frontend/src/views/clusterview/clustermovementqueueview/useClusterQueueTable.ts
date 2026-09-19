/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import {MovementPurpose} from '@/api/movements'
import type {ClusterQueueEntry} from '@/api/clusterInventory'
import {useDataTable} from '@/composables/useDataTable'

/**
 * The steps waiting on the association as a table: the step, the station it came from, the piece,
 * the kind of movement and when it was raised. Until a column is sorted the rows keep the order the
 * server lists them in.
 *
 * @param entries the steps as the server lists them
 */
export function useClusterQueueTable(entries: () => ClusterQueueEntry[]) {
    const {t} = useI18n()

    const columns = computed<TableColumn<ClusterQueueEntry>[]>(() => [
        {
            key: 'step', label: t('movements.queue.columns.step'), type: ColumnTypes.TEXT, pinned: true,
            value: entry => entry.stepLabel ?? t('clusterMovements.unnamedStep'),
        },
        {key: 'station', label: t('clusterMovements.station'), type: ColumnTypes.TEXT, value: entry => entry.stationName},
        {key: 'item', label: t('movements.queue.columns.what'), type: ColumnTypes.TEXT, value: entry => entry.itemName},
        {
            key: 'purpose', label: t('movements.queue.columns.purpose'), type: ColumnTypes.ENUM,
            value: entry => entry.purpose,
            options: Object.values(MovementPurpose).map(purpose => ({
                value: purpose,
                label: t(`clusterMovements.purpose.${purpose}`),
            })),
        },
        {key: 'created', label: t('movements.queue.columns.created'), type: ColumnTypes.DATE, value: entry => entry.createdAt},
    ])

    return useDataTable<ClusterQueueEntry>({
        id: 'cluster-movements',
        rows: entries,
        columns,
        rowKey: entry => entry.movementId,
    })
}
