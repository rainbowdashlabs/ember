/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import WaitingSectionToolbar from './waitingsection/WaitingSectionToolbar.vue'
import WaitingSectionTable from './waitingsection/WaitingSectionTable.vue'
import type { WaitingListEntryWithScore, WaitingListField } from '@/api/waitingList'
import { useDataTable } from '@/composables/useDataTable'
import { byDate } from '@/composables/useSortable'
import { fieldIdOfColumn, SCORE_KEY, waitingColumns } from './waitingsection/waitingColumns'

/**
 * The people waiting for a place, highest score first.
 *
 * <p>Which questions show as columns is kept on the list itself rather than in the browser, so
 * everybody who looks after the list sees the same ones; ticking one in the column list asks the
 * list to change that.
 */
const props = defineProps<{
  entries: WaitingListEntryWithScore[]
  fields: WaitingListField[]
  visibleFieldIds: Set<number>
  readonly?: boolean
  canAdd?: boolean
}>()

const emit = defineEmits<{
  invite: [entryId: number]
  backToWaiting: [entryId: number]
  moveToTesting: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
  setFields: [fieldIds: number[], visible: boolean]
  addEntry: []
}>()

const { t } = useI18n()

const anyBelowJoinAge = computed(() => props.entries.some(e => e.belowJoinAge))
const hideBelowJoinAge = ref(false)

const shown = computed(() =>
    hideBelowJoinAge.value ? props.entries.filter(e => !e.belowJoinAge) : props.entries)

const table = useDataTable<WaitingListEntryWithScore>({
  id: 'waiting-list',
  rows: shown,
  columns: computed(() => waitingColumns({ t, fields: props.fields, visibleFieldIds: props.visibleFieldIds })),
  rowKey: item => item.entry.id,
  sort: { key: SCORE_KEY, direction: 'desc' },
  fallbackSort: byDate(item => item.entry.createdAt),
})

function setColumns(keys: (string | number)[], visible: boolean) {
  const fieldIds = keys.map(fieldIdOfColumn).filter((fieldId): fieldId is number => fieldId !== null)
  if (fieldIds.length > 0) emit('setFields', fieldIds, visible)
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <WaitingSectionToolbar
      :entries-count="entries.length"
      :column-options="table.pickerOptions"
      :can-add="canAdd"
      @set-columns="setColumns"
      @add-entry="emit('addEntry')"
    />

    <div v-if="anyBelowJoinAge" class="flex items-center gap-2">
      <ToggleInput v-model="hideBelowJoinAge"/>
      <span class="text-sm">{{ t('waitingList.hideBelowJoinAge') }}</span>
    </div>

    <EmptyState compact v-if="entries.length === 0">{{ t('waitingList.noEntries') }}</EmptyState>

    <WaitingSectionTable
      v-else
      :table="table"
      :readonly="readonly"
      @invite="(id) => emit('invite', id)"
      @back-to-waiting="(id) => emit('backToWaiting', id)"
      @move-to-testing="(id) => emit('moveToTesting', id)"
      @navigate-to-entry="(id) => emit('navigateToEntry', id)"
      @delete-entry="(entry) => emit('deleteEntry', entry)"
    />
  </NeutralContainer>
</template>
