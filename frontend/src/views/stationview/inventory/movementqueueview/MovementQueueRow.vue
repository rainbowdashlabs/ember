/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MovementRow from './MovementRow.vue'
import {MovementState, type Movement} from '@/api/movements'

/**
 * One row of the queue: what it is about, and what this reader may do with it.
 *
 * <p>The step is answered from the row rather than from a page of its own, because a queue is worked
 * through rather than read: opening each movement to press one button is the thing this replaces.
 */
const props = defineProps<{
  movement: Movement
  /** Whether the row names who it is with, which only somebody working the whole queue needs. */
  showMember: boolean
  /** Whether this reader may put a movement right, which is the manager's alone. */
  canCorrect: boolean
}>()

const emit = defineEmits<{
  acknowledge: []
  correct: []
  open: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer
      :data-movement="props.movement.id"
      class="grid gap-2 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)_auto]"
      data-testid="movement-row"
  >
    <MovementRow :movement="props.movement" :show-member="props.showMember"/>
    <div class="flex flex-wrap items-center justify-end gap-2">
      <PrimaryButton
          v-if="props.movement.actionable && props.movement.state === MovementState.OPEN"
          class="text-xs"
          data-testid="movement-acknowledge"
          @click="emit('acknowledge')"
      >
        {{ t('movements.queue.acknowledge') }}
      </PrimaryButton>
      <SecondaryButton
          v-if="props.canCorrect && props.movement.state === MovementState.OPEN"
          class="text-xs"
          data-testid="movement-correct"
          @click="emit('correct')"
      >
        {{ t('movements.queue.correct') }}
      </SecondaryButton>
      <SecondaryButton class="text-xs" data-testid="movement-open" @click="emit('open')">
        {{ t('movements.queue.openDetail') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
