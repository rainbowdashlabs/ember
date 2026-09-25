/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import RowLink from '@/components/navigation/RowLink.vue'
import type {BoardTicket, TicketPriorityName} from '@/api/boards'

const props = defineProps<{
  results: BoardTicket[]
  shortKey: string
  /** Where a hit leads, which is the ticket on this board. */
  ticketPage: (ticket: BoardTicket) => string
  laneName: (laneId: number) => string
  priorityIcon: (priority: TicketPriorityName) => string[]
  priorityColor: (priority: TicketPriorityName) => string
}>()

defineEmits<{
  pick: []
}>()

void props
</script>

<template>
  <div
      v-if="results.length > 0"
      class="absolute z-20 mt-1 w-[28rem] right-0 rounded-theme border border-(--border) bg-(--bg) shadow-lg overflow-hidden"
  >
    <RowLink v-for="result in results" :key="result.id" :to="ticketPage(result)">
      <div
          class="px-3 py-2 text-sm cursor-pointer hover:bg-primary/5 flex items-center gap-2"
          @click="$emit('pick')"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-1.5">
            <span class="font-mono text-xs text-(--text-muted) shrink-0">{{ shortKey }}-{{ result.ticketNumber }}</span>
            <span class="truncate">{{ result.title }}</span>
          </div>
        </div>
        <div class="flex items-center gap-2 shrink-0 text-xs text-(--text-muted)">
          <span class="px-1.5 py-0.5 rounded bg-(--bg-accent) text-[0.65rem]">{{ laneName(result.laneId) }}</span>
          <font-awesome-icon :icon="priorityIcon(result.priority)" :class="priorityColor(result.priority)"/>
        </div>
      </div>
    </RowLink>
  </div>
</template>
