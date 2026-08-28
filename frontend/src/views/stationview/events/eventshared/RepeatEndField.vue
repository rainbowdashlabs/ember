/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * When a repeating appointment stops repeating.
 *
 * <p>A last day and a number of times are two ways of saying the same thing, so the choice sets one
 * and clears the other. A course of eight evenings is said as eight, a summer of Saturday duties as
 * the day it ends, and until one of them was possible both had to be deleted by hand on the day.
 */
const until = defineModel<string>('until', {required: true})
const count = defineModel<number | undefined>('count')

const {t} = useI18n()

const RepeatEndKind = {
  NEVER: 'never',
  ON_DATE: 'onDate',
  AFTER_COUNT: 'afterCount',
} as const

type RepeatEndKindName = (typeof RepeatEndKind)[keyof typeof RepeatEndKind]

const kind = computed<RepeatEndKindName>(() => {
  if (until.value) return RepeatEndKind.ON_DATE
  if (count.value != null) return RepeatEndKind.AFTER_COUNT
  return RepeatEndKind.NEVER
})

function choose(chosen: string | number | null | undefined) {
  until.value = ''
  count.value = chosen === RepeatEndKind.AFTER_COUNT ? 10 : undefined
}
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('events.repeatEnd') }}</FieldLabel>
    <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <SelectInput :model-value="kind" class="w-full" data-testid="repeat-end-kind" @update:model-value="choose">
        <option :value="RepeatEndKind.NEVER">{{ t('events.repeatEndNever') }}</option>
        <option :value="RepeatEndKind.ON_DATE">{{ t('events.repeatEndOnDate') }}</option>
        <option :value="RepeatEndKind.AFTER_COUNT">{{ t('events.repeatEndAfterCount') }}</option>
      </SelectInput>

      <DateInput v-if="kind === RepeatEndKind.ON_DATE" v-model="until" data-testid="repeat-end-until"/>
      <NumberInput
          v-else-if="kind === RepeatEndKind.AFTER_COUNT"
          :model-value="count"
          data-testid="repeat-end-count"
          @update:model-value="count = $event && $event > 0 ? $event : 1"
      />
    </div>
    <MutedText v-if="kind === RepeatEndKind.AFTER_COUNT" size="sm" tag="p">
      {{ t('events.repeatEndCountHint') }}
    </MutedText>
  </div>
</template>
