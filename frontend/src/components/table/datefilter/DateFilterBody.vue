/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateFilterTree from './DateFilterTree.vue'
import {joinDateTokens, splitDateTokens, type DateFilterTokens} from '@/util/dateFilter'

/**
 * The body of a date column's filter: the from/before range, the year/month/day tree, and for a
 * birth date the two age bounds. Everything reads and writes the one token set the column filter
 * state keeps, so applying and saving stay exactly what they are for every other column.
 */
defineProps<{
  /** The column's distinct raw values, feeding the tree. */
  values: string[]
  /** Offers the age bounds, which only a birth date can answer. */
  birthDate?: boolean
}>()

const selected = defineModel<Set<string>>({required: true})

const {t} = useI18n()

const tokens = computed(() => splitDateTokens(selected.value))

function patch(change: Partial<DateFilterTokens>) {
  selected.value = joinDateTokens({...tokens.value, ...change})
}

const prefixes = computed({
  get: () => tokens.value.prefixes,
  set: value => patch({prefixes: value}),
})
const from = computed({
  get: () => tokens.value.from ?? '',
  set: value => patch({from: value || null}),
})
const before = computed({
  get: () => tokens.value.before ?? '',
  set: value => patch({before: value || null}),
})

function ageBound(key: 'ageNowMin' | 'ageNowBelow' | 'ageEoyMin' | 'ageEoyBelow') {
  return computed<number | undefined>({
    get: () => tokens.value[key] ?? undefined,
    set: value => patch({[key]: typeof value === 'number' && Number.isFinite(value) ? value : null}),
  })
}

const ageNowMin = ageBound('ageNowMin')
const ageNowBelow = ageBound('ageNowBelow')
const ageEoyMin = ageBound('ageEoyMin')
const ageEoyBelow = ageBound('ageEoyBelow')
</script>

<template>
  <div class="space-y-3">
    <div>
      <FieldLabel>{{ t('tableFilter.range') }}</FieldLabel>
      <div class="grid grid-cols-2 gap-2">
        <label class="block text-xs">
          <span class="text-(--text-muted)">{{ t('tableFilter.from') }}</span>
          <DateInput v-model="from" data-testid="date-filter-from"/>
        </label>
        <label class="block text-xs">
          <span class="text-(--text-muted)">{{ t('tableFilter.before') }}</span>
          <DateInput v-model="before" data-testid="date-filter-before"/>
        </label>
      </div>
    </div>

    <template v-if="birthDate">
      <div>
        <FieldLabel>{{ t('tableFilter.currentAge') }}</FieldLabel>
        <div class="grid grid-cols-2 gap-2">
          <label class="block text-xs">
            <span class="text-(--text-muted)">{{ t('tableFilter.ageAtLeast') }}</span>
            <NumberInput v-model="ageNowMin" data-testid="age-now-min"/>
          </label>
          <label class="block text-xs">
            <span class="text-(--text-muted)">{{ t('tableFilter.ageBelow') }}</span>
            <NumberInput v-model="ageNowBelow" data-testid="age-now-below"/>
          </label>
        </div>
      </div>
      <div>
        <FieldLabel>{{ t('tableFilter.calendarAge') }}</FieldLabel>
        <div class="grid grid-cols-2 gap-2">
          <label class="block text-xs">
            <span class="text-(--text-muted)">{{ t('tableFilter.ageAtLeast') }}</span>
            <NumberInput v-model="ageEoyMin" data-testid="age-eoy-min"/>
          </label>
          <label class="block text-xs">
            <span class="text-(--text-muted)">{{ t('tableFilter.ageBelow') }}</span>
            <NumberInput v-model="ageEoyBelow" data-testid="age-eoy-below"/>
          </label>
        </div>
        <p class="mt-1 text-xs text-(--text-muted)">{{ t('tableFilter.calendarAgeHint') }}</p>
      </div>
    </template>

    <div class="max-h-64 overflow-y-auto rounded border border-bg-light-accent p-2 dark:border-bg-dark-accent">
      <DateFilterTree v-model="prefixes" :values="values"/>
    </div>
  </div>
</template>
