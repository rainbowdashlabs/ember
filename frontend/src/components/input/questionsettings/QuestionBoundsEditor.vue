/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'

/**
 * The smallest and largest number a field takes, and where a feature offers it, what it steps by.
 *
 * <p>A step below one is what lets a number take a fraction; without one, a number is whole. That is
 * the one rule the server checks a number by, so a measurement and a count need no second type.
 */
const min = defineModel<number | null | undefined>('min', {required: true})
const max = defineModel<number | null | undefined>('max', {required: true})
const step = defineModel<number | null | undefined>('step', {default: undefined})

defineProps<{
  /** Whether the step is offered at all. */
  withStep?: boolean
}>()

const {t} = useI18n()

function numberOrNull(value: unknown): number | null {
  return value === undefined || value === null || value === '' ? null : Number(value)
}
</script>

<template>
  <div class="flex flex-wrap gap-4">
    <LabelledField :label="t('questionSettings.min')" class="w-32">
      <NumberInput :model-value="min ?? undefined" data-testid="question-min"
                   @update:model-value="value => min = numberOrNull(value)"/>
    </LabelledField>
    <LabelledField :label="t('questionSettings.max')" class="w-32">
      <NumberInput :model-value="max ?? undefined" data-testid="question-max"
                   @update:model-value="value => max = numberOrNull(value)"/>
    </LabelledField>
    <LabelledField v-if="withStep" :help="t('questionSettings.stepHint')" :label="t('questionSettings.step')"
                   class="w-48">
      <DecimalInput :model-value="step ?? undefined" data-testid="question-step"
                    @update:model-value="value => step = numberOrNull(value)"/>
    </LabelledField>
  </div>
</template>
