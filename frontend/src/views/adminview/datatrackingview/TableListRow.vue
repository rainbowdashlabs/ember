/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {DataTracking, TableEntry} from '@/api/dataTracking'
import StatusBadge from './StatusBadge.vue'

interface Row {
  name: string
  entry: TableEntry
  hasUnverifiedColumns: boolean
  needsReview: boolean
  rowKey: string
}

interface StrategyChip {
  strategy: string
  column: string
  reason: string
  cascadeFrom?: {table: string; effective: string}
}

const props = defineProps<{
  row: Row
  selected: boolean
  search: string
  tracking: DataTracking | null
}>()

const emit = defineEmits<{
  (e: 'open'): void
  (e: 'toggle-batch'): void
}>()

const {t} = useI18n()

// -- Helpers ----------------------------------------------------------------

const query = computed(() => props.search.trim().toLowerCase())

const matchedColumns = computed<string[]>(() => {
  const q = query.value
  if (!q) return []
  return props.row.entry.columns
      .filter(c => c.name.toLowerCase().includes(q) || (c.description ?? '').toLowerCase().includes(q))
      .map(c => c.name)
})

function isForeignKeyColumn(column: string): boolean {
  return (props.row.entry.foreignKeys ?? []).some(fk => fk.column === column)
}

const strategyChips = computed<StrategyChip[]>(() => {
  const strategies = props.row.entry.gdprDeletion?.strategies
  if (!strategies?.length) return []
  const seen = new Set<string>()
  const out: StrategyChip[] = []
  for (const s of strategies) {
    const key = `${s.strategy}|${s.column}`
    if (!s.strategy || seen.has(key)) continue
    seen.add(key)
    const chip: StrategyChip = {strategy: s.strategy, column: s.column, reason: s.reason ?? ''}
    if (s.strategy === 'CASCADE') chip.cascadeFrom = resolveCascadeParent(s.column)
    out.push(chip)
  }
  return out
})

function resolveCascadeParent(column: string): {table: string; effective: string} | undefined {
  const fk = props.row.entry.foreignKeys?.find(f => f.column === column)
  if (!fk || !props.tracking) return undefined
  const parent = props.tracking.tables[fk.refTable]
  if (!parent) return {table: fk.refTable, effective: 'unknown'}
  const parentStrategies = parent.gdprDeletion?.strategies ?? []
  if (parentStrategies.length === 0) return {table: fk.refTable, effective: 'NONE'}
  const order = ['DELETE_EXPLICIT', 'CASCADE', 'NULL', 'ANONYMIZE', 'RETAIN_UNLINKED', 'RETAIN', 'NOT_APPLICABLE']
  let best = parentStrategies[0]!.strategy
  for (const ps of parentStrategies) {
    if (order.indexOf(ps.strategy) < order.indexOf(best)) best = ps.strategy
  }
  return {table: fk.refTable, effective: best}
}

function isCascadeMisleading(chip: StrategyChip): boolean {
  if (chip.strategy !== 'CASCADE' || !chip.cascadeFrom) return false
  return ['ANONYMIZE', 'RETAIN', 'RETAIN_UNLINKED', 'NOT_APPLICABLE', 'NONE'].includes(chip.cascadeFrom.effective)
}

function strategyClasses(strategy: string): string {
  switch (strategy) {
    case 'CASCADE':
    case 'DELETE_EXPLICIT':
      return 'bg-(--bg-accent) text-(--text-muted) border border-(--border)'
    case 'ANONYMIZE':
      return 'bg-[#73CEFF]/15 text-[#3694FF] border border-[#73CEFF]/40'
    case 'NULL':
      return 'bg-[#ffdd1b]/15 text-[#a07a00] border border-[#ffdd1b]/50 dark:text-[#ffdd1b]'
    case 'RETAIN':
      return 'bg-[#ec2929]/15 text-[#ec2929] border border-[#ec2929]/40'
    case 'RETAIN_UNLINKED':
      return 'bg-[#ec2929]/10 text-[#ec2929] border border-[#ec2929]/30'
    default:
      return 'bg-(--bg-accent) text-(--text-muted) border border-(--border) opacity-60'
  }
}

function strategyTooltip(chip: StrategyChip): string {
  const parts: string[] = [t('adminDataTracking.strategyTooltip', {strategy: chip.strategy})]
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
  <div
      class="flex items-center justify-between gap-3 px-3 py-2 rounded-theme border border-(--border) hover:bg-(--bg-accent) cursor-pointer transition-colors"
      :class="{
        'border-l-4 border-l-error': row.entry.stationTransfer.status === 'UNVERIFIED',
        'border-l-4 border-l-warning': row.entry.stationTransfer.status === 'TRACKED' && row.hasUnverifiedColumns,
        'bg-(--accent)/5': selected,
      }"
      @click="emit('open')"
  >
    <input
        type="checkbox"
        class="cursor-pointer"
        :checked="selected"
        :aria-label="t('adminDataTracking.batch.toggleRow')"
        @click.stop="emit('toggle-batch')"
    />
    <div class="min-w-0 flex-1">
      <div class="font-mono text-sm font-semibold truncate">{{ row.name }}</div>
      <div
          v-if="row.entry.description"
          class="text-xs text-(--text-muted) italic truncate"
          :title="row.entry.description"
      >
        {{ row.entry.description }}
      </div>
      <div class="text-xs text-(--text-muted)">
        {{ row.entry.columns.length }} cols
        <span v-if="row.entry.foreignKeys?.length"> · {{ row.entry.foreignKeys.length }} fk</span>
        <span v-if="row.entry.lookups?.length"> · {{ row.entry.lookups.length }} lookups</span>
        <span v-if="row.entry.outputShape && row.entry.outputShape !== 'ROWS'">· {{ row.entry.outputShape }}</span>
        <span v-if="row.entry.customScope"> · customScope</span>
      </div>
      <div
          v-if="row.entry.foreignKeys?.length"
          class="text-xs font-mono mt-0.5 flex items-center gap-1 flex-wrap"
      >
        <span
            v-for="fk in row.entry.foreignKeys"
            :key="fk.column"
            class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded bg-(--bg-accent) text-(--text-muted)"
            :title="`FK → ${fk.refTable}.${fk.refColumn} (${fk.onDelete})`"
        >
          <font-awesome-icon :icon="['fas', 'key']" class="text-primary"/>
          <span>{{ fk.column }}</span>
        </span>
      </div>
      <div
          v-if="matchedColumns.length"
          class="text-xs font-mono text-(--accent) mt-0.5 truncate flex items-center gap-1 flex-wrap"
      >
        <span>↳</span>
        <span
            v-for="col in matchedColumns"
            :key="col"
            class="inline-flex items-center gap-0.5"
        >
          <font-awesome-icon
              v-if="isForeignKeyColumn(col)"
              :icon="['fas', 'key']"
              class="opacity-70"
              :title="t('adminDataTracking.foreignKeyHint')"
          />
          <span>{{ col }}</span>
        </span>
      </div>
    </div>
    <div class="flex items-center gap-1 flex-wrap justify-end">
      <StatusBadge :status="row.entry.stationTransfer.status" :label="t('adminDataTracking.shortLabel.transfer')"/>
      <StatusBadge :status="row.entry.gdprExport.status" :label="t('adminDataTracking.shortLabel.gdprE')"/>
      <StatusBadge :status="row.entry.gdprDeletion.status" :label="t('adminDataTracking.shortLabel.gdprD')"/>
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
          v-if="row.hasUnverifiedColumns"
          class="ml-1 text-xs font-mono text-[#ec2929]"
          :title="t('adminDataTracking.unverifiedColumns')"
      >
        !cols
      </span>
    </div>
  </div>
</template>
