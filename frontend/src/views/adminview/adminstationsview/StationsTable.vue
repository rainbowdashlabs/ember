/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type {AdminStationSummary} from '@/api/generated/schema'
import type {DataTableApi} from '@/composables/useDataTable'
import type {Failure} from '@/util/failure'
import StationStateBadge from './StationStateBadge.vue'
import ManagerReminder from './ManagerReminder.vue'

/**
 * The stations as a table, the same facts as the tiles, with the reminder, edit and delete per row. The
 * last reminder has a column of its own, so the row carries the reminder's button alone, and a refused
 * reminder is said once above the table rather than inside a cell.
 */
defineProps<{
  table: DataTableApi<AdminStationSummary>
  onReminded?: (station: AdminStationSummary) => void
}>()

defineEmits<{
  edit: [id: string]
  delete: [station: AdminStationSummary]
}>()

const {t} = useI18n()

const failure = ref<Failure | null>(null)

function setFailure(value: Failure | null) {
  failure.value = value
}
</script>

<template>
  <div class="space-y-3">
    <FailureAlert :failure="failure"/>
    <RecordTable :table="table" row-test-id="station-row" test-id="stations-table">
      <template #cell-state="{row}">
        <StationStateBadge :state="row.state"/>
      </template>
      <template #actions="{row}">
        <div class="flex items-start justify-end gap-2">
          <ManagerReminder :station="row" compact :on-reminded="onReminded" :on-failure="setFailure"/>
          <EditButton @click="$emit('edit', row.id)"/>
          <DeleteButton @click="$emit('delete', row)"/>
        </div>
      </template>
      <template #empty>
        <EmptyState>{{ t('adminStations.emptyFilter') }}</EmptyState>
      </template>
    </RecordTable>
  </div>
</template>
