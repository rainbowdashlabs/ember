/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import PitchBadge from './PitchBadge.vue'
import type {PitchTable} from './pitchTypes'

/** The application's own table frame, fed from slide data. */
defineProps<{
  table: PitchTable
}>()

function initials(text: string): string {
  return text.split(' ').slice(0, 2).map(part => part.charAt(0)).join('')
}
</script>

<template>
  <DataTable plain>
    <template #head>
      <Th v-if="table.actions"/>
      <Th v-for="column in table.columns" :key="column">{{ column }}</Th>
    </template>
    <TRow v-for="(row, index) in table.rows" :key="index">
      <Td v-if="table.actions">
        <div class="flex items-center gap-1">
          <IconButton :icon="['fas', 'eye']" label="Details" class="text-primary hover:bg-primary/15"/>
          <EditButton/>
        </div>
      </Td>
      <Td v-for="(cell, cellIndex) in row" :key="cellIndex" :muted="cell.muted">
        <PitchBadge v-if="cell.badge" :tone="cell.badge">{{ cell.text }}</PitchBadge>
        <span v-else class="inline-flex items-center gap-1.5">
          <span v-if="cell.avatar"
                class="inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary/15
                       text-[10px] font-bold text-primary">{{ initials(cell.text) }}</span>
          <span :class="cell.strong ? 'font-medium' : ''">{{ cell.text }}</span>
          <PitchBadge v-if="cell.note" :tone="cell.note.tone">{{ cell.note.text }}</PitchBadge>
        </span>
      </Td>
    </TRow>
  </DataTable>
</template>
