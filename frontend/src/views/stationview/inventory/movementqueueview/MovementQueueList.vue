/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ColumnPickerButton from '@/components/table/ColumnPickerButton.vue'
import type {DataTableApi} from '@/composables/useDataTable'
import type {Movement} from '@/api/movements'
import MovementRecordTable from './MovementRecordTable.vue'
import MovementRowActions from './MovementRowActions.vue'

/**
 * The rows of the queue, each with what this reader may do with it.
 *
 * <p>The step is answered from the row rather than from a page of its own, because a queue is worked
 * through rather than read. Two empty states rather than one: a queue with nothing in it and a queue
 * whose filters hide everything are different situations, and the second one is a filter to undo.
 */
const props = defineProps<{
  table: DataTableApi<Movement>
  /** How many movements the station has, which decides whether the queue is empty or merely narrowed. */
  total: number
  canCorrect: boolean
  picking: boolean
  pickedIds: Set<number>
}>()

const emit = defineEmits<{
  acknowledge: [movement: Movement]
  correct: [movement: Movement]
  open: [movement: Movement]
  pick: [movement: Movement]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <div class="flex justify-end">
      <ColumnPickerButton :options="props.table.pickerOptions" @toggle="props.table.toggleColumn"/>
    </div>
    <MovementRecordTable :table="props.table" test-id="movement-queue">
      <template #actions="{row}">
        <MovementRowActions
            :can-correct="props.canCorrect"
            :movement="row"
            :picked="props.pickedIds.has(row.id)"
            :picking="props.picking"
            @acknowledge="emit('acknowledge', row)"
            @correct="emit('correct', row)"
            @open="emit('open', row)"
            @pick="emit('pick', row)"
        />
      </template>
      <template #empty>
        <EmptyState>{{ props.total === 0 ? t('movements.queue.empty') : t('movements.queue.noMatch') }}</EmptyState>
      </template>
    </MovementRecordTable>
  </div>
</template>
