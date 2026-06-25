/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import StatusBadge from '../StatusBadge.vue'
import type {TableEntry} from '@/api/dataTracking'

export interface StrategyChip {
  strategy: string
  column: string
  reason: string
  cascadeFrom?: {table: string; effective: string}
}

defineProps<{
  entry: TableEntry
  hasUnverifiedColumns: boolean
  strategyChips: StrategyChip[]
  strategyClasses: (strategy: string) => string
  strategyTooltip: (chip: StrategyChip) => string
  isCascadeMisleading: (chip: StrategyChip) => boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center gap-1 flex-wrap justify-end">
    <StatusBadge :status="entry.stationTransfer.status" :label="t('adminDataTracking.shortLabel.transfer')"/>
    <StatusBadge :status="entry.gdprExport.status" :label="t('adminDataTracking.shortLabel.gdprE')"/>
    <StatusBadge :status="entry.gdprDeletion.status" :label="t('adminDataTracking.shortLabel.gdprD')"/>
    <span
        v-for="chip in strategyChips"
        :key="chip.strategy + ':' + chip.column"
        class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[10px] font-mono font-semibold"
        :class="strategyClasses(chip.strategy)"
        :title="strategyTooltip(chip)"
    >
      <span>{{ chip.strategy }}</span>
      <span
          v-if="chip.cascadeFrom"
          class="opacity-70"
      >
        ← {{ chip.cascadeFrom.table }} ({{ chip.cascadeFrom.effective }})
      </span>
      <font-awesome-icon
          v-if="isCascadeMisleading(chip)"
          :icon="['fas', 'triangle-exclamation']"
          class="text-[#a07a00] dark:text-[#ffdd1b]"
          :title="t('adminDataTracking.cascadeMisleading')"
      />
    </span>
    <span
        v-if="hasUnverifiedColumns"
        class="ml-1 text-xs font-mono text-[#ec2929]"
        :title="t('adminDataTracking.unverifiedColumns')"
    >
      !cols
    </span>
  </div>
</template>
