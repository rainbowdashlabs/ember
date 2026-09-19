/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup generic="Row">
import {computed, useSlots} from 'vue'
import DataTable from './DataTable.vue'
import HeaderFilterCell from './HeaderFilterCell.vue'
import Td from './Td.vue'
import Th from './Th.vue'
import type {DataTableApi} from '@/composables/useDataTable'

/** The rows of a {@link RecordTable} as a table, for a screen wide enough to hold one. */
const props = defineProps<{
  table: DataTableApi<Row>
  rows: readonly Row[]
  testId: string
  rowTestId: string
  plain: boolean
  clickable: boolean
  rowClass?: (row: Row) => string
}>()

const emit = defineEmits<{
  'row-click': [row: Row]
}>()

const slots = useSlots()

const span = computed(() =>
    props.table.visibleColumns.length + (slots.lead ? 1 : 0) + (slots.actions ? 1 : 0))

function rowClasses(row: Row): string[] {
  return [
    'border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50',
    props.clickable ? 'cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30' : '',
    props.rowClass?.(row) ?? '',
  ]
}
</script>

<template>
  <DataTable :data-testid="testId" :plain="plain">
    <template #head>
      <Th v-if="$slots.lead"><slot name="lead-head"/></Th>
      <Th
          v-for="column in table.visibleColumns"
          :key="column.key"
          :align="column.align"
          :aria-sort="table.ariaSort(column.key)"
      >
        <HeaderFilterCell
            :has-filter="table.hasFilter(column.key)"
            :label="column.label"
            :show-filter="table.isFilterable(column)"
            :show-sort="table.isSortable(column)"
            :sort-active="table.ariaSort(column.key) !== 'none'"
            :sort-icon="table.sortIcon(column.key)"
            @filter="table.openFilter(column.key)"
            @sort="table.toggleSort(column.key)"
        />
      </Th>
      <Th v-if="$slots.actions" align="right"><slot name="actions-head"/></Th>
    </template>
    <template v-for="row in rows" :key="table.rowKey(row)">
      <tr :class="rowClasses(row)" :data-testid="rowTestId" @click="clickable && emit('row-click', row)">
        <Td v-if="$slots.lead"><slot :row="row" name="lead"/></Td>
        <Td v-for="column in table.visibleColumns" :key="column.key" :align="column.align">
          <slot :name="`cell-${column.key}`" :row="row" :text="table.display(column, row)">
            {{ table.display(column, row) }}
          </slot>
        </Td>
        <Td v-if="$slots.actions" align="right"><slot :row="row" name="actions"/></Td>
      </tr>
      <slot :row="row" :span="span" name="after-row"/>
    </template>
    <tr v-if="rows.length === 0 && $slots.empty">
      <Td :colspan="span"><slot name="empty"/></Td>
    </tr>
  </DataTable>
</template>
