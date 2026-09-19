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
import {joinNumberTokens, splitNumberTokens} from './columnFilter'

/**
 * The body of a number column's filter: a lower and an upper bound, both included.
 *
 * <p>Setting either bound replaces single values a filter saved before numbers had a range, since
 * the two together would narrow to a list the reader can no longer see.
 */
const selected = defineModel<Set<string>>({required: true})

const {t} = useI18n()

const tokens = computed(() => splitNumberTokens(selected.value))

function bound(which: 'min' | 'max') {
  return computed<number | undefined>({
    get: () => tokens.value[which] ?? undefined,
    set: value => {
      const next = typeof value === 'number' && Number.isFinite(value) ? value : null
      const {min, max} = tokens.value
      selected.value = which === 'min' ? joinNumberTokens(next, max) : joinNumberTokens(min, next)
    },
  })
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
  </div>
</template>
