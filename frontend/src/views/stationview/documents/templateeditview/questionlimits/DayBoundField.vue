/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import type {DayBound} from '@/api/generated/schema'
import {todayIso} from '@/components/documents/questionLimits'

/**
 * One bound of a date question: none, a fixed day, or a number of days from the day a document is
 * generated. A bound relative to that day stays right for as long as the template is used, which a
 * fixed day does not.
 */
const bound = defineModel<DayBound | null>({required: true})

defineProps<{
  label: string
}>()

const {t} = useI18n()

type Sort = 'none' | 'day' | 'fromToday'

const SORTS: readonly Sort[] = ['none', 'day', 'fromToday']

const sort = computed<Sort>(() => {
  if (bound.value?.day) return 'day'
  if (bound.value?.fromToday !== null && bound.value?.fromToday !== undefined) return 'fromToday'
  return 'none'
})

function setSort(value: string | number | null | undefined) {
  if (value === 'day') setDay(todayIso())
  else if (value === 'fromToday') bound.value = {day: null, fromToday: 0}
  else bound.value = null
}

function setDay(day: string | undefined) {
  bound.value = day ? {day, fromToday: null} : null
}

function setDays(value: unknown) {
  bound.value = {day: null, fromToday: value === '' || value === undefined ? 0 : Math.trunc(Number(value))}
}
</script>

<template>
  <LabelledField :label="label" :help="sort === 'fromToday' ? t('documentTemplates.questions.limits.fromTodayHelp') : undefined">
    <div class="flex gap-2">
      <SelectInput :model-value="sort" data-testid="day-bound-sort" @update:model-value="setSort">
        <option v-for="each in SORTS" :key="each" :value="each">{{ t(`documentTemplates.questions.limits.boundSort.${each}`) }}</option>
      </SelectInput>
      <DateInput v-if="sort === 'day'" :model-value="bound?.day ?? ''" data-testid="day-bound-day"
                 @update:model-value="setDay"/>
      <NumberInput v-if="sort === 'fromToday'" :model-value="bound?.fromToday ?? 0" data-testid="day-bound-days"
                   @update:model-value="setDays"/>
    </div>
  </LabelledField>
</template>
