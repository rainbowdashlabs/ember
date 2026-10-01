/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {joinNumberTokens, splitNumberTokens, type FilterChoice} from './columnFilter'

/**
 * The body of a number column's filter: a lower and an upper bound, both included, and the values
 * the rows hold to tick one by one. A row passes when it lies within the bounds and, where values
 * are ticked, is one of them, so a short column such as an age narrows by picking its values.
 */
const props = defineProps<{
  /** The values the column's rows hold, in their order. */
  choices: FilterChoice[]
}>()

const selected = defineModel<Set<string>>({required: true})

const {t} = useI18n()

const tokens = computed(() => splitNumberTokens(selected.value))

function bound(which: 'min' | 'max') {
  return computed<number | undefined>({
    get: () => tokens.value[which] ?? undefined,
    set: value => {
      const next = typeof value === 'number' && Number.isFinite(value) ? value : null
      const {min, max, exact} = tokens.value
      const bounds = which === 'min' ? joinNumberTokens(next, max) : joinNumberTokens(min, next)
      selected.value = new Set([...bounds, ...exact])
    },
  })
}

function toggle(value: string) {
  const next = new Set(selected.value)
  if (next.has(value)) next.delete(value)
  else next.add(value)
  selected.value = next
}

const min = bound('min')
const max = bound('max')
</script>

<template>
  <div>
    <FieldLabel>{{ t('tableFilter.numberRange') }}</FieldLabel>
    <div class="grid grid-cols-2 gap-2">
      <label class="block text-xs">
        <span class="text-(--text-muted)">{{ t('tableFilter.atLeast') }}</span>
        <DecimalInput v-model="min" data-testid="number-filter-min" step="any"/>
      </label>
      <label class="block text-xs">
        <span class="text-(--text-muted)">{{ t('tableFilter.atMost') }}</span>
        <DecimalInput v-model="max" data-testid="number-filter-max" step="any"/>
      </label>
    </div>
    <div v-if="props.choices.length > 0" class="mt-3 max-h-48 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
      <FieldLabel
          v-for="choice in props.choices"
          :key="choice.value"
          inline
          class="cursor-pointer px-2 py-1 rounded hover:bg-bg-light-accent/50 dark:hover:bg-bg-dark-accent/50 text-xs"
      >
        <CheckboxInput :model-value="selected.has(choice.value)" @update:model-value="toggle(choice.value)"/>
        <span>{{ choice.label }}</span>
      </FieldLabel>
    </div>
  </div>
</template>
