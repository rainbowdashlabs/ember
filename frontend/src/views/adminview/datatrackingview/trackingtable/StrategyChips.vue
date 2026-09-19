/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {DataTracking, TableEntry} from '@/api/dataTracking'
import {isCascadeMisleading, strategyChipsOf, strategyClasses, type StrategyChip} from './strategyChips'

/**
 * The deletion strategies one table carries as a strip of chips. A cascade names the table it hangs
 * on and warns where that table is never deleted from, so the cascade will never fire.
 */
const props = defineProps<{
  entry: TableEntry
  tracking: DataTracking | null
}>()

const {t} = useI18n()

const chips = computed(() => strategyChipsOf(props.entry, props.tracking))

function tooltipOf(chip: StrategyChip): string {
  const parts = [t('adminDataTracking.strategyTooltip', {strategy: chip.strategy})]
  parts.push(`${t('adminDataTracking.detail.strategyColumn')}: ${chip.column}`)
  if (chip.reason) parts.push(chip.reason)
  if (chip.cascadeFrom) {
    parts.push(t('adminDataTracking.cascadeFromTooltip', {table: chip.cascadeFrom.table, effective: chip.cascadeFrom.effective}))
    if (isCascadeMisleading(chip)) parts.push(t('adminDataTracking.cascadeMisleading'))
  }
  return parts.join('\n')
}
</script>

<template>
  <span class="inline-flex items-center gap-1 flex-wrap">
    <span
        v-for="chip in chips"
        :key="chip.strategy + ':' + chip.column"
        class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-mono font-semibold"
        :class="strategyClasses(chip.strategy)"
        :title="tooltipOf(chip)"
    >
      <span>{{ chip.strategy }}</span>
      <span v-if="chip.cascadeFrom" class="opacity-70">
        ← {{ chip.cascadeFrom.table }} ({{ chip.cascadeFrom.effective }})
      </span>
      <font-awesome-icon
          v-if="isCascadeMisleading(chip)"
          :icon="['fas', 'triangle-exclamation']"
          class="text-[#a07a00] dark:text-[#ffdd1b]"
          :title="t('adminDataTracking.cascadeMisleading')"
      />
    </span>
  </span>
</template>
