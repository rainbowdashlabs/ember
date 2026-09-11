/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {RestorePlanMovement, RestorePlanStep} from '@/api/movements'

/**
 * Where the movements on each step of the old chain go once it is replaced.
 *
 * <p>One row per step rather than per movement. Where a movement lands follows from the step it
 * stands on and from nothing else, so every movement on one step takes the same answer and asking
 * per movement would be the same question over and over.
 */
const {t} = useI18n()

const props = defineProps<{
  movements: RestorePlanMovement[]
  steps: RestorePlanStep[]
  /** The step picked for each old step so far, absent where nobody has picked one yet. */
  choices: Record<number, number | null>
}>()

const emit = defineEmits<{choose: [stepId: number | null, stepIndex: number | null]}>()

const anyUncertain = computed(() => props.movements.some(landing => !landing.certain))

/** The key a landing is held under, since the step of a movement that has lost one is nothing. */
function keyOf(landing: RestorePlanMovement): number {
  return landing.stepId ?? -1
}

function pick(stepId: number | null, value: string | number | null | undefined) {
  emit('choose', stepId, value === null || value === undefined || value === '' ? null : Number(value))
}
</script>

<template>
  <div class="space-y-2">
    <MutedText size="sm" tag="p">{{ t('flows.restoreMovementsHint') }}</MutedText>
    <MutedText v-if="anyUncertain" size="sm" tag="p">{{ t('flows.restoreUncertainHint') }}</MutedText>

    <div
        v-for="landing in props.movements"
        :key="keyOf(landing)"
        class="space-y-1 rounded-md border border-(--border) p-2"
        data-testid="flow-restore-mapping"
    >
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-sm">
          <span class="text-(--text-muted)">{{ t('flows.restoreCurrentStep') }}: </span>
          <span class="font-medium">{{ landing.standingOn || t('flows.restoreStepGone') }}</span>
        </span>
        <ErrorBadge v-if="!landing.certain">{{ t('flows.restoreUncertain') }}</ErrorBadge>
      </div>
      <MutedText size="xs" tag="p">{{ t('flows.restoreAffects', {count: landing.movements}) }}</MutedText>
      <FieldLabel>{{ t('flows.restoreTargetStep') }}</FieldLabel>
      <SelectInput
          :model-value="props.choices[keyOf(landing)] ?? null"
          class="w-full"
          @update:model-value="value => pick(landing.stepId, value)"
      >
        <option :value="null">{{ t('flows.restoreChooseStep') }}</option>
        <option v-for="step in props.steps" :key="step.index" :value="step.index">
          {{ step.index + 1 }}. {{ step.label }}
        </option>
      </SelectInput>
    </div>
  </div>
</template>
