/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import EventChipButton from '@/components/button/EventChipButton.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import type {StationEvent} from '@/api/types'

interface DayCellData {
  date: Date
  iso: string
  isCurrentMonth: boolean
  isToday: boolean
  events: {event: StationEvent; date: string}[]
}

const props = defineProps<{
  cell: DayCellData
  dayIdx: number
  laneCount: number
  chipStyle: (ev: StationEvent) => {backgroundColor: string; color: string} | undefined
  formatTime: (iso?: string) => string
}>()

defineEmits<{
  open: [ev: StationEvent, date: string]
}>()

void props
</script>

<template>
  <div
      class="min-h-16 sm:min-h-24 lg:min-h-28 border border-(--border) rounded p-0.5 sm:p-1.5 flex flex-col gap-0.5 overflow-hidden"
      :class="[
        cell.isCurrentMonth ? 'bg-(--bg)' : 'bg-(--bg-accent) opacity-50',
        cell.isToday ? 'ring-2 ring-primary' : '',
      ]"
      :style="{gridColumn: dayIdx + 1, gridRow: `1 / span ${laneCount + 2}`}"
  >
    <div class="flex items-center justify-between">
      <span
          class="text-xs sm:text-sm font-semibold leading-none"
          :class="cell.isToday ? 'text-primary' : ''"
      >{{ cell.date.getDate() }}</span>
      <span
          v-if="cell.events.length > 3"
          class="text-[9px] sm:text-[10px] text-(--text-muted) font-medium"
      >+{{ cell.events.length - 3 }}</span>
    </div>
    <div :style="{height: `${laneCount * 1.25}rem`}" aria-hidden="true"/>
    <div class="flex flex-col gap-0.5 overflow-hidden min-h-0">
      <EventChipButton
          v-for="evRef in cell.events.slice(0, 3)"
          :key="`${evRef.event.id}-${evRef.date}`"
          :title="`${evRef.event.name}${evRef.event.startTime ? ' · ' + formatTime(evRef.event.startTime) : ''}`"
          :custom-style="chipStyle(evRef.event)"
          @click="$emit('open', evRef.event, evRef.date)"
      >
        <MutedIcon v-if="evRef.event.restricted" :icon="['fas', 'lock']" class="mr-0.5 inline"/>
        <span class="hidden sm:inline">{{ formatTime(evRef.event.startTime) }}&nbsp;</span>
        <span>{{ evRef.event.name }}</span>
      </EventChipButton>
    </div>
  </div>
</template>
