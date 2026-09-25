/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import EventChipLink from '@/components/navigation/EventChipLink.vue'
import type {StationEvent} from '@/api/events'
import type {MultiDayBar} from '@/composables/useEventCalendarGrid'

const props = defineProps<{
  bar: MultiDayBar
  chipStyle: (ev: StationEvent) => {backgroundColor: string; color: string} | undefined
  /** Where the bar leads on the day the appointment starts, or nothing where it may not be opened. */
  detailRoute: (ev: StationEvent, date: string) => RouteLocationRaw | null
  formatTime: (iso?: string) => string
}>()

void props
</script>

<template>
  <EventChipLink
      class="relative z-10 leading-tight px-1 self-center"
      :class="[
        bar.continuesLeft ? 'rounded-l-none ml-0' : 'rounded-l ml-0.5',
        bar.continuesRight ? 'rounded-r-none mr-0' : 'rounded-r mr-0.5',
      ]"
      :style="{
        gridColumnStart: bar.startCol,
        gridColumnEnd: bar.endCol + 1,
        gridRow: bar.lane + 2,
      }"
      :custom-style="chipStyle(bar.event)"
      :title="`${bar.event.name}${bar.event.startTime ? ' · ' + formatTime(bar.event.startTime) : ''}`"
      :to="detailRoute(bar.event, bar.startIso)"
  >
    <font-awesome-icon v-if="bar.event.restricted" :icon="['fas', 'lock']" class="mr-0.5 inline h-2.5 w-2.5"/>
    {{ bar.event.name }}
  </EventChipLink>
</template>
