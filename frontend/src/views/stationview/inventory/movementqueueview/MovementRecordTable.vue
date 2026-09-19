/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import RecordTable from '@/components/table/RecordTable.vue'
import type {DataTableApi} from '@/composables/useDataTable'
import type {Movement} from '@/api/movements'
import MovementMemberCell from './MovementMemberCell.vue'
import MovementPurposeCell from './MovementPurposeCell.vue'
import MovementStandingCell from './MovementStandingCell.vue'
import MovementSubjectCell from './MovementSubjectCell.vue'
import {MovementColumn} from './movementColumns'

/**
 * A table of movements, with the cells every list of them draws the same way: the piece, the kind
 * with whose gear it is, the member, and where it stands. A screen adds its own buttons as the
 * `actions` of a row and its own words for an empty list.
 */
const props = withDefaults(defineProps<{
  table: DataTableApi<Movement>
  testId?: string
  clickable?: boolean
}>(), {
  testId: 'movement-table',
  clickable: false,
})

const emit = defineEmits<{
  'row-click': [movement: Movement]
}>()

defineSlots<{
  actions?: (scope: {row: Movement}) => unknown
  empty?: () => unknown
}>()
</script>

<template>
  <RecordTable
      :clickable="props.clickable"
      :table="props.table"
      :test-id="props.testId"
      row-test-id="movement-row"
      @row-click="emit('row-click', $event)"
  >
    <template #[`cell-${MovementColumn.SUBJECT}`]="{row}">
      <MovementSubjectCell :movement="row"/>
    </template>
    <template #[`cell-${MovementColumn.PURPOSE}`]="{row}">
      <MovementPurposeCell :movement="row"/>
    </template>
    <template #[`cell-${MovementColumn.MEMBER}`]="{row, text}">
      <MovementMemberCell :movement="row" :text="text"/>
    </template>
    <template #[`cell-${MovementColumn.STANDING}`]="{row}">
      <MovementStandingCell :movement="row"/>
    </template>
    <template v-if="$slots.actions" #actions="{row}">
      <slot :row="row" name="actions"/>
    </template>
    <template v-if="$slots.empty" #empty>
      <slot name="empty"/>
    </template>
  </RecordTable>
</template>
