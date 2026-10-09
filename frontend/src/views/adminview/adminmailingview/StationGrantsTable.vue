/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {InstanceMailStation} from '@/api/generated/schema'
import type {DataTableApi} from '@/composables/useDataTable'

/**
 * The stations, each ticked into the selection the bulk actions act on. A station that may send
 * through the instance's providers is marked at its edge.
 */
defineProps<{
  table: DataTableApi<InstanceMailStation>
  selected: ReadonlySet<string>
}>()

const emit = defineEmits<{
  toggle: [stationUid: string]
}>()

const {t} = useI18n()

function rowClass(station: InstanceMailStation): string {
  return station.granted ? 'border-l-4 border-l-(--color-success)' : ''
}
</script>

<template>
  <RecordTable :row-class="rowClass" :table="table" plain test-id="instance-mail-stations">
    <template #lead="{row}">
      <CheckboxInput
          :aria-label="t('instanceMail.stations.select', {name: row.name})"
          :model-value="selected.has(row.stationUid)"
          @update:model-value="emit('toggle', row.stationUid)"
      />
    </template>
    <template #cell-dailyLimit="{row}">
      {{ row.dailyLimit ?? t('instanceMail.stations.noLimit') }}
    </template>
    <template #empty>
      <EmptyHint>{{ t('instanceMail.stations.none') }}</EmptyHint>
    </template>
  </RecordTable>
</template>
