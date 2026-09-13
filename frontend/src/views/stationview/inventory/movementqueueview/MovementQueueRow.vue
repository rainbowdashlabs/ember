/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MovementRow from './MovementRow.vue'
import MovementRowActions from './MovementRowActions.vue'
import type {Movement} from '@/api/movements'

/**
 * One row of the queue on a wide screen: the columns, and what this reader may do with it.
 *
 * <p>The step is answered from the row rather than from a page of its own, because a queue is worked
 * through rather than read: opening each movement to press one button is the thing this replaces.
 *
 * <p>Every track of the grid is fixed or fractional and none of them is sized by content: each row is
 * a grid of its own, so a column that measures what is in it comes out a different width on every
 * row, and a list of them reads as a list of nothing lined up.
 */
const props = defineProps<{
  movement: Movement
  /** Whether the row names who it is with, which only somebody working the whole queue needs. */
  showMember: boolean
  /** Whether this reader may put a movement right, which is the manager's alone. */
  canCorrect: boolean
  /** Whether rows are being ticked for an export, which is when the buttons give way to a tick. */
  picking?: boolean
  picked?: boolean
}>()

const emit = defineEmits<{
  acknowledge: []
  correct: []
  open: []
  pick: []
}>()
</script>

<template>
  <NeutralContainer
      :data-movement="props.movement.id"
      class="grid grid-cols-[5rem_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.1fr)_minmax(0,0.9fr)_minmax(0,1.5fr)] gap-2"
      data-testid="movement-row"
  >
    <MovementRow :movement="props.movement" :show-member="props.showMember"/>
    <MovementRowActions
        :can-correct="props.canCorrect"
        :movement="props.movement"
        :picked="props.picked"
        :picking="props.picking"
        @acknowledge="emit('acknowledge')"
        @correct="emit('correct')"
        @open="emit('open')"
        @pick="emit('pick')"
    />
  </NeutralContainer>
</template>
