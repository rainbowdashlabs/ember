/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {MovementState, type Movement} from '@/api/movements'

/**
 * What can be done with one movement from the list it is in.
 *
 * <p>The step it waits on is on the button, because pressing it is what makes those words true. While
 * rows are being ticked for an export the buttons give way to the tick: the list is being read then,
 * not worked through.
 */
const props = defineProps<{
  movement: Movement
  canCorrect: boolean
  picking?: boolean
  picked?: boolean
}>()

const emit = defineEmits<{
  acknowledge: []
  correct: []
  open: []
  pick: []
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="props.picking" class="flex items-center justify-end">
    <CheckboxInput :model-value="props.picked ?? false" data-testid="movement-pick"
                   @update:model-value="emit('pick')">
      {{ t('movements.queue.pickForExport') }}
    </CheckboxInput>
  </div>
  <div v-else class="flex flex-wrap items-center gap-2 sm:justify-end">
    <PrimaryButton
        v-if="props.movement.actionable && props.movement.state === MovementState.OPEN"
        class="text-xs"
        data-testid="movement-acknowledge"
        @click="emit('acknowledge')"
    >
      {{ props.movement.currentStepLabel ?? t('movements.queue.acknowledge') }}
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
</template>
