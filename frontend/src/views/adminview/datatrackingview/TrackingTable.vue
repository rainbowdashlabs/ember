/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {TrackingStatus, type DataTracking} from '@/api/dataTracking'
import type {DataTableApi} from '@/composables/useDataTable'
import StatusBadge from './StatusBadge.vue'
import StrategyChips from './trackingtable/StrategyChips.vue'
import TableRowSummary from './trackingtable/TableRowSummary.vue'
import {TRACKING_CONTEXTS} from './trackingtable/trackingColumns'
import type {TrackingRow} from './trackingtable/trackingRow'

/**
 * The tracked tables, each ticked into the batch or opened into its details with a press. A table
 * whose transfer nobody has verified is marked red at its edge, one with unverified columns amber.
 */
const props = defineProps<{
  table: DataTableApi<TrackingRow>
  selected: ReadonlySet<string>
  tracking: DataTracking | null
}>()

const emit = defineEmits<{
  open: [name: string]
  toggleBatch: [name: string]
}>()

const {t} = useI18n()

function rowClass(row: TrackingRow): string {
  const edge = row.entry.stationTransfer.status === TrackingStatus.UNVERIFIED ? 'border-l-4 border-l-error'
      : row.unverifiedColumns > 0 ? 'border-l-4 border-l-warning' : ''
  return [edge, props.selected.has(row.name) ? 'bg-(--accent)/5' : ''].join(' ')
}
</script>

<template>
  <RecordTable :row-class="rowClass" :table="table" clickable test-id="data-tracking-table" @row-click="emit('open', $event.name)">
    <template #lead="{row}">
      <span @click.stop>
        <CheckboxInput
            :aria-label="t('adminDataTracking.batch.toggleRow')"
            :model-value="selected.has(row.name)"
            @update:model-value="emit('toggleBatch', row.name)"
        />
      </span>
    </template>
    <template #cell-name="{row}">
      <TableRowSummary :row="row" :search="table.search"/>
    </template>
    <template v-for="(context, key) in TRACKING_CONTEXTS" :key="key" #[`cell-${key}`]="{row}">
      <StatusBadge :status="context.statusOf(row.entry)"/>
    </template>
    <template #cell-strategies="{row}">
      <StrategyChips :entry="row.entry" :tracking="tracking"/>
    </template>
    <template #cell-unverifiedColumns="{text}">
      <span class="text-xs font-mono text-[#ec2929]">{{ text }}</span>
    </template>
    <template #empty>
      <EmptyState>{{ t('adminDataTracking.noMatches') }}</EmptyState>
    </template>
  </RecordTable>
</template>
