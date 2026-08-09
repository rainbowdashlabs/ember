/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {isRecurringEvent, type EventBreak, type EventCategory, type StationEvent} from '@/api/events'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CalendarDayCell from '@/views/stationview/events/upcomingview/eventscalendar/CalendarDayCell.vue'
import CalendarMultiDayBar from '@/views/stationview/events/upcomingview/eventscalendar/CalendarMultiDayBar.vue'
import {useEventCalendarGrid} from '@/composables/useEventCalendarGrid'
import {contrastingTextColorForHex} from '@/util/contrastColor'
import {formatTime} from '@/util/format'

/**
 * Month-grid calendar of upcoming events. The grid itself — which occurrence falls on which day,
 * how multi-day events are stacked into lanes, and which month is in view — is built by
 * {@link useEventCalendarGrid}; this component renders it and colours the chips by category.
 */
const props = defineProps<{
  allEvents: StationEvent[]
  eventBreaks: EventBreak[]
  eligibleMemberIds: Record<number, number[]>
  currentMemberId: number
  managedMemberIds: number[]
  selectedCategoryId: string
  searchQuery: string
  categories?: EventCategory[]
}>()

const {t} = useI18n()
const router = useRouter()

const {viewYear, viewMonth, weeks, prevMonth, nextMonth, goToToday} = useEventCalendarGrid(
    computed(() => ({
      allEvents: props.allEvents,
      eventBreaks: props.eventBreaks,
      eligibleMemberIds: props.eligibleMemberIds,
      currentMemberId: props.currentMemberId,
      managedMemberIds: props.managedMemberIds,
      selectedCategoryId: props.selectedCategoryId,
      searchQuery: props.searchQuery,
    })),
)

const monthNames = [
  'Januar',
  'Februar',
  'März',
  'April',
  'Mai',
  'Juni',
  'Juli',
  'August',
  'September',
  'Oktober',
  'November',
  'Dezember',
]
const weekdayHeader = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']

const categoryStyle = computed<Record<number, {bg: string; fg: string}>>(() => {
  const out: Record<number, {bg: string; fg: string}> = {}
  for (const cat of props.categories ?? []) {
    if (!cat.color) continue
    const fg = contrastingTextColorForHex(cat.color)
    if (!fg) continue
    out[cat.id] = {bg: cat.color, fg}
  }
  return out
})

function chipStyle(ev: StationEvent): {backgroundColor: string; color: string} | undefined {
  if (ev.categoryId == null) return undefined
  const s = categoryStyle.value[ev.categoryId]
  if (!s) return undefined
  return {backgroundColor: s.bg, color: s.fg}
}

function openEvent(ev: StationEvent, date: string) {
  if (isRecurringEvent(ev.eventType)) {
    router.push({name: 'event-detail-date', params: {id: ev.id, date}})
  } else {
    router.push({name: 'event-detail', params: {id: ev.id}})
  }
}
</script>

<template>
  <NeutralContainer :padded="false" class="space-y-2 sm:space-y-3 p-1 sm:p-4">
    <div class="flex items-center justify-between gap-2 flex-wrap px-1 sm:px-0">
      <div class="flex items-center gap-2 flex-wrap">
        <SecondaryButton :icon="['fas', 'chevron-left']" :title="t('eventsUpcoming.calendarPrev')" @click="prevMonth">
          <span class="sr-only">{{ t('eventsUpcoming.calendarPrev') }}</span>
        </SecondaryButton>
        <span class="font-semibold text-base whitespace-nowrap min-w-32 text-center">
          {{ monthNames[viewMonth] }} {{ viewYear }}
        </span>
        <SecondaryButton :icon="['fas', 'chevron-right']" :title="t('eventsUpcoming.calendarNext')" @click="nextMonth">
          <span class="sr-only">{{ t('eventsUpcoming.calendarNext') }}</span>
        </SecondaryButton>
      </div>
      <SecondaryButton :icon="['fas', 'calendar']" @click="goToToday">
        {{ t('eventsUpcoming.calendarToday') }}
      </SecondaryButton>
    </div>

    <div class="grid grid-cols-7 gap-0.5 sm:gap-1 text-[10px] sm:text-xs font-semibold text-(--text-muted)">
      <div v-for="d in weekdayHeader" :key="d" class="text-center py-1">{{ d }}</div>
    </div>

    <div class="space-y-0.5 sm:space-y-1">
      <div
          v-for="(week, weekIdx) in weeks"
          :key="weekIdx"
          class="grid grid-cols-7 gap-0.5 sm:gap-1"
          :style="{
            gridTemplateRows: `auto ${'1.25rem '.repeat(week.laneCount)}1fr`,
          }"
      >
        <CalendarDayCell
            v-for="(cell, dayIdx) in week.days"
            :key="cell.iso"
            :cell="cell"
            :day-idx="dayIdx"
            :lane-count="week.laneCount"
            :chip-style="chipStyle"
            :format-time="formatTime"
            @open="openEvent"
        />
        <CalendarMultiDayBar
            v-for="(bar, barIdx) in week.bars"
            :key="`bar-${weekIdx}-${barIdx}`"
            :bar="bar"
            :chip-style="chipStyle"
            :format-time="formatTime"
            @open="openEvent"
        />
      </div>
    </div>
  </NeutralContainer>
</template>
