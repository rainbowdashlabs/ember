/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type MaybeRefOrGetter} from 'vue'
import {useI18n} from 'vue-i18n'
import type {ClusterItem} from '@/api/clusterInventory'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'
import {useDataTable} from '@/composables/useDataTable'

/** Where a piece that is away from the association can be, in the order they sort in. */
const AWAY_CUSTODIES = ['AT_STATION', 'WITH_MEMBER', 'WITH_PARTNER', 'IN_TRANSIT', 'LOST'] as const

/**
 * The association's pieces that are out at its stations as one table, which the screen shows a
 * block per station. The columns, sort and filters chosen in one block hold for all of them.
 */
export function useOutItemTable(items: MaybeRefOrGetter<readonly ClusterItem[]>) {
    const {t} = useI18n()

    const columns = computed<TableColumn<ClusterItem>[]>(() => [
        {key: 'name', label: t('clusterInventory.outColumn.name'), type: ColumnTypes.TEXT, value: item => item.name, pinned: true},
        {key: 'internalId', label: t('clusterInventory.outColumn.internalId'), type: ColumnTypes.TEXT, value: item => item.internalId, defaultVisible: false},
        {key: 'size', label: t('clusterInventory.outColumn.size'), type: ColumnTypes.TEXT, value: item => item.sizeLabel},
        {key: 'holder', label: t('clusterInventory.outColumn.holder'), type: ColumnTypes.TEXT, value: item => item.holderName},
        {
            key: 'custody', label: t('clusterInventory.outColumn.custody'), type: ColumnTypes.ENUM, value: item => item.custody,
            options: enumOptions(AWAY_CUSTODIES, custody => t(`clusterInventory.custody.${custody}`)),
        },
    ])

    return useDataTable<ClusterItem>({
        id: 'cluster-items-out',
        rows: items,
        columns,
        rowKey: item => item.id,
        sort: {key: 'name'},
        perStation: false,
    })
}

export type OutItemTableApi = ReturnType<typeof useOutItemTable>
