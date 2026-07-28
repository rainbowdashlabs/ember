/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import EventChipButton from '@/components/button/EventChipButton.vue'
import type {StationEvent} from '@/api/events'

interface MultiDayBarData {
  event: StationEvent
  startCol: number
  endCol: number
  lane: number
  continuesLeft: boolean
  continuesRight: boolean
  startIso: string
}

const props = defineProps<{
  bar: MultiDayBarData
  chipStyle: (ev: StationEvent) => {backgroundColor: string; color: string} | undefined
  formatTime: (iso?: string) => string
}>()

defineEmits<{
  open: [ev: StationEvent, date: string]
}>()

void props
</script>

<template>
  <EventChipButton
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
      @click="$emit('open', bar.event, bar.startIso)"
  >
    <font-awesome-icon v-if="bar.event.restricted" :icon="['fas', 'lock']" class="mr-0.5 inline h-2.5 w-2.5"/>
    {{ bar.event.name }}
  </EventChipButton>
</template>
