/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {matchedColumnsOf, type TrackingRow} from './trackingRow'

/**
 * A tracked table's name with what it is: its description, how much it holds, its foreign keys,
 * and while searching, the columns the search found in it.
 */
const props = defineProps<{
  row: TrackingRow
  search: string
}>()

const {t} = useI18n()

const matchedColumns = computed(() => matchedColumnsOf(props.row.entry, props.search))

function isForeignKeyColumn(column: string): boolean {
  return (props.row.entry.foreignKeys ?? []).some(fk => fk.column === column)
}
</script>

<template>
  <div class="min-w-0">
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
      <span v-for="col in matchedColumns" :key="col" class="inline-flex items-center gap-0.5">
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
</template>
