/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MovementQueueRow from './MovementQueueRow.vue'
import MovementQueueCard from './MovementQueueCard.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import type {Movement} from '@/api/movements'

/**
 * The rows themselves, as columns on a wide screen and as cards on a narrow one.
 *
 * <p>Two empty states rather than one: a queue with nothing in it and a queue whose filters hide
 * everything are different situations, and the second one is a filter to undo.
 */
const props = defineProps<{
  /** Everything the station has, which decides whether the queue is empty or merely narrowed. */
  all: Movement[]
  visible: Movement[]
  showMember: boolean
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
const {isMobile} = useBreakpoint()
</script>

<template>
  <EmptyState v-if="props.all.length === 0">{{ t('movements.queue.empty') }}</EmptyState>
  <EmptyState v-else-if="props.visible.length === 0">{{ t('movements.queue.noMatch') }}</EmptyState>

  <component
      :is="isMobile ? MovementQueueCard : MovementQueueRow"
      v-for="movement in props.visible"
      :key="movement.id"
      :can-correct="props.canCorrect"
      :movement="movement"
      :picked="props.pickedIds.has(movement.id)"
      :picking="props.picking"
      :show-member="props.showMember"
      @acknowledge="emit('acknowledge', movement)"
      @correct="emit('correct', movement)"
      @open="emit('open', movement)"
      @pick="emit('pick', movement)"
  />
</template>
