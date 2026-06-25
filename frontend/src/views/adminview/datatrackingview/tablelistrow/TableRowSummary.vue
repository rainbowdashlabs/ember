/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import type {TableEntry} from '@/api/dataTracking'

defineProps<{
  name: string
  entry: TableEntry
  matchedColumns: string[]
  isForeignKeyColumn: (column: string) => boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="min-w-0 flex-1">
    <div class="font-mono text-sm font-semibold truncate">{{ name }}</div>
    <div
        v-if="entry.description"
        class="text-xs text-(--text-muted) italic truncate"
        :title="entry.description"
    >
      {{ entry.description }}
    </div>
    <div class="text-xs text-(--text-muted)">
      {{ entry.columns.length }} cols
      <span v-if="entry.foreignKeys?.length"> · {{ entry.foreignKeys.length }} fk</span>
      <span v-if="entry.lookups?.length"> · {{ entry.lookups.length }} lookups</span>
      <span v-if="entry.outputShape && entry.outputShape !== 'ROWS'">· {{ entry.outputShape }}</span>
      <span v-if="entry.customScope"> · customScope</span>
    </div>
    <div
        v-if="entry.foreignKeys?.length"
        class="text-xs font-mono mt-0.5 flex items-center gap-1 flex-wrap"
    >
      <span
          v-for="fk in entry.foreignKeys"
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
</template>
