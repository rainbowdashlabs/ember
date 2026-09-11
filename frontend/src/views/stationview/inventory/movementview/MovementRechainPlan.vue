/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {RechainPlan} from '@/api/movements'

/**
 * The two chains, the step the movement stands on, and the step it is to land on.
 *
 * <p>Both chains are named because the words of a step say nothing on their own: a step called
 * "An den Träger geschickt" reads the same whichever chain it was written for.
 */
const {t} = useI18n()

const props = defineProps<{
  plan: RechainPlan
  /** The step picked so far, counted from the front, or null while nobody has picked one. */
  stepIndex: number | null
}>()

const emit = defineEmits<{choose: [stepIndex: number | null]}>()

function pick(value: string | number | null | undefined) {
  emit('choose', value === null || value === undefined || value === '' ? null : Number(value))
}
</script>

<template>
  <div class="space-y-3">
    <MutedText size="sm" tag="p">{{ t('movements.rechain.hint') }}</MutedText>

    <div class="space-y-1 text-sm">
      <div>
        <span class="text-(--text-muted)">{{ t('movements.rechain.currentFlow') }}: </span>
        {{ props.plan.currentFlowName || t('movements.rechain.noFlow') }}
      </div>
      <div>
        <span class="text-(--text-muted)">{{ t('movements.rechain.standingOn') }}: </span>
        {{ props.plan.standingOn || t('movements.rechain.noStep') }}
      </div>
      <div>
        <span class="text-(--text-muted)">{{ t('movements.rechain.targetFlow') }}: </span>
        {{ props.plan.targetFlowName || t('movements.rechain.noFlow') }}
      </div>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('movements.rechain.targetStep') }}</FieldLabel>
      <SelectInput
          :model-value="props.stepIndex"
          class="w-full"
          data-testid="movement-rechain-step"
          @update:model-value="pick"
      >
        <option :value="null">{{ t('movements.rechain.chooseStep') }}</option>
        <option v-for="step in props.plan.steps" :key="step.index" :value="step.index">
          {{ step.index + 1 }}. {{ step.label }}
        </option>
      </SelectInput>
    </div>
  </div>
</template>
