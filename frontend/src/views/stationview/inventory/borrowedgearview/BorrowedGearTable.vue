/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'
import type {BorrowedItem} from '@/api/inventory'
import {byValue} from '@/composables/useSortable'
import {useDataTable} from '@/composables/useDataTable'
import {formatDate} from '@/util/format'

/**
 * The gear borrowed from partner stations.
 *
 * <p>It opens sorted by the partner, which is the order the page exists for: with several partners
 * the origin lives on the row rather than in a heading, so one partner's gear has to come together.
 * Within one partner the pieces stand by name.
 */
const props = defineProps<{rows: BorrowedItem[]}>()

const {t} = useI18n()

const columns = computed<TableColumn<BorrowedItem>[]>(() => [
  {key: 'name', label: t('inventory.borrowed.colName'), type: ColumnTypes.TEXT, value: row => row.item.name, pinned: true},
  {key: 'internalId', label: t('inventory.borrowed.colId'), type: ColumnTypes.TEXT, value: row => row.item.internalId},
  {key: 'owner', label: t('inventory.borrowed.colOwner'), type: ColumnTypes.TEXT, value: row => row.ownerStationName},
  {
    key: 'due', label: t('inventory.borrowed.colDue'), type: ColumnTypes.DATE, value: row => row.dueOn,
    display: row => row.dueOn ? formatDate(row.dueOn) : t('inventory.borrowed.noDueDate'),
  },
])

const table = useDataTable<BorrowedItem>({
  id: 'inventory-borrowed',
  rows: () => props.rows,
  columns,
  rowKey: row => row.item.id,
  fallbackSort: byValue(row => row.item.name),
  sort: {key: 'owner'},
})
</script>

<template>
  <RecordTable :table="table" test-id="borrowed-gear-table">
    <template #cell-owner="{text}">
      <PrimaryBadge>{{ text }}</PrimaryBadge>
    </template>
  </RecordTable>
</template>
