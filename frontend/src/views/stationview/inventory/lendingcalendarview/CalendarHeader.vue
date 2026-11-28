/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {weekdayName} from '@/util/format'

/**
 * The day labels of the calendar. A drag across them starts a block period for the whole station;
 * the days of today and of weekends are set apart.
 */
const props = defineProps<{
  days: string[]
  today: string
  dragging: {from: string; to: string} | null
}>()

const emit = defineEmits<{
  dragStart: [day: string]
  dragOver: [day: string]
}>()

function weekday(day: string): number {
  return ((new Date(`${day}T00:00:00Z`).getUTCDay() + 6) % 7) + 1
}

function start(event: PointerEvent, day: string) {
  if (event.button === 0) emit('dragStart', day)
}

function inDrag(day: string): boolean {
  return !!props.dragging && day >= props.dragging.from && day <= props.dragging.to
}
</script>

<template>
  <div class="grid select-none text-center text-xs" :style="{gridTemplateColumns: `repeat(${days.length}, minmax(2rem, 1fr))`}"
       data-testid="lending-calendar-header">
    <div v-for="day in days" :key="day" class="cursor-crosshair border-l border-(--border) py-1" :data-day="day"
         :class="[day === today ? 'font-semibold text-primary' : '', weekday(day) > 5 ? 'bg-bg-light-accent/50 dark:bg-bg-dark-accent/50' : '',
                  inDrag(day) ? 'bg-info/30' : '']"
         @pointerdown.prevent="start($event, day)" @pointerenter="emit('dragOver', day)">
      <span class="block text-(--text-muted)">{{ weekdayName(weekday(day)).slice(0, 2) }}</span>
      <span class="block">{{ Number(day.slice(8, 10)) }}</span>
    </div>
  </div>
</template>
