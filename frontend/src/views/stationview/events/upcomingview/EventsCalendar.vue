/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import type {EventBreak, EventCategory, StationEvent} from '@/api/types'
import {EventTypes, isRecurringEvent} from '@/api/types'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EventChipButton from '@/components/button/EventChipButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {contrastingTextColorForHex} from '@/util/contrastColor'

/**
 * Month-grid calendar of upcoming events. Always renders a 7×6 grid (42 cells) so the layout
 * stays stable regardless of how many days the visible month has. Days from the previous /
 * next month fill the leading and trailing rows, dimmed so the user can tell them apart.
 *
 * Occurrences are computed client-side from the full event list, mirroring the dashboard's
 * UpcomingEventsPanel logic — one-time events match their start date; recurring patterns
 * (RECURRING / MONTHLY_FIRST / QUARTERLY / YEARLY) are unrolled based on dayOfWeek and the
 * date's position in the month / year. Event breaks blank out their date range.
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

// categoryId -> {bg, fg}. Computed once per props change so we don't re-derive contrast
// colors per cell. Categories without a color fall back to undefined and the chip renders
// with the default primary tint.
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

const {t} = useI18n()
const router = useRouter()

const today = new Date()
const viewYear = ref(today.getFullYear())
const viewMonth = ref(today.getMonth()) // 0-based

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
// Monday-first week to match German convention.
const weekdayHeader = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

const todayIso = isoDate(today)

interface DayCell {
  date: Date
  iso: string
  isCurrentMonth: boolean
  isToday: boolean
  events: {event: StationEvent; date: string}[]
}

function isRelevant(eventId: number): boolean {
  const eligible = props.eligibleMemberIds[eventId]
  // No restriction (eligible undefined) → visible to everyone. Otherwise the current member
  // or any of their managed members must be in the eligible set.
  if (eligible === undefined) return true
  if (eligible.includes(props.currentMemberId)) return true
  return props.managedMemberIds.some(id => eligible.includes(id))
}

function matchesFilters(ev: StationEvent): boolean {
  if (props.selectedCategoryId && ev.categoryId !== Number(props.selectedCategoryId)) return false
  if (!props.searchQuery) return true
  const q = props.searchQuery.toLowerCase()
  return !!(ev.name?.toLowerCase().includes(q) || ev.description?.toLowerCase().includes(q))
}

function eventsForDate(date: Date): {event: StationEvent; date: string}[] {
  const dateStr = isoDate(date)
  // ISO weekday: Monday=1 .. Sunday=7. JS getDay() returns Sunday=0..Saturday=6.
  const dow = date.getDay() === 0 ? 7 : date.getDay()
  const dayOfMonth = date.getDate()
  const month = date.getMonth()
  const inBreak = props.eventBreaks.some(
      b => b.startDate && b.endDate && dateStr >= b.startDate && dateStr <= b.endDate,
  )
  if (inBreak) return []

  const result: {event: StationEvent; date: string}[] = []
  for (const ev of props.allEvents) {
    if (!isRelevant(ev.id) || !matchesFilters(ev)) continue

    if (ev.eventType === EventTypes.ONE_TIME) {
      if (!ev.startTime) continue
      const startStr = new Date(ev.startTime).toISOString().slice(0, 10)
      // Multi-day one-time events span from startTime's date to endTime's date.
      // Render the chip on every day in that range, not just the start.
      const endStr = ev.endTime ? new Date(ev.endTime).toISOString().slice(0, 10) : startStr
      if (dateStr >= startStr && dateStr <= endStr) result.push({event: ev, date: dateStr})
      continue
    }
    if (!isRecurringEvent(ev.eventType)) continue
    if (!ev.dayOfWeek || ev.dayOfWeek !== dow) continue

    if (ev.eventType === EventTypes.RECURRING) {
      result.push({event: ev, date: dateStr})
    } else if (ev.eventType === EventTypes.MONTHLY_FIRST) {
      if (dayOfMonth <= 7) result.push({event: ev, date: dateStr})
    } else if (ev.eventType === EventTypes.QUARTERLY) {
      // First matching weekday in the first week of a quarter (month % 3 === 0).
      if (dayOfMonth <= 7 && month % 3 === 0) result.push({event: ev, date: dateStr})
    } else if (ev.eventType === EventTypes.YEARLY && ev.startTime) {
      const ref = new Date(ev.startTime)
      if (ref.getMonth() === month && ref.getDate() === dayOfMonth) {
        result.push({event: ev, date: dateStr})
      }
    }
  }
  // Sort by start time within each day so the cell reads top-down chronologically.
  return result.sort((a, b) => (a.event.startTime ?? '').localeCompare(b.event.startTime ?? ''))
}

const cells = computed((): DayCell[] => {
  const firstOfMonth = new Date(viewYear.value, viewMonth.value, 1)
  // Map JS Sunday-first weekday to Monday-first offset: Mon=0 .. Sun=6.
  const firstWeekdayMon = (firstOfMonth.getDay() + 6) % 7
  const result: DayCell[] = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(viewYear.value, viewMonth.value, 1 - firstWeekdayMon + i)
    const iso = isoDate(d)
    result.push({
      date: d,
      iso,
      isCurrentMonth: d.getMonth() === viewMonth.value && d.getFullYear() === viewYear.value,
      isToday: iso === todayIso,
      events: eventsForDate(d),
    })
  }
  // Trim a trailing row that's entirely next-month dates — happens when February starts on
  // a Monday, or whenever the month ends right at the end of a week. Keeps the grid as
  // compact as possible without ever exposing a "ghost" all-next-month row.
  const lastRow = result.slice(35)
  if (lastRow.every(c => !c.isCurrentMonth)) {
    return result.slice(0, 35)
  }
  return result
})

function prevMonth() {
  if (viewMonth.value === 0) {
    viewYear.value--
    viewMonth.value = 11
  } else {
    viewMonth.value--
  }
}

function nextMonth() {
  if (viewMonth.value === 11) {
    viewYear.value++
    viewMonth.value = 0
  } else {
    viewMonth.value++
  }
}

function goToToday() {
  viewYear.value = today.getFullYear()
  viewMonth.value = today.getMonth()
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
  <!-- padded=false because the calendar grid edges already need every horizontal pixel on
       mobile; we add our own tighter padding only on sm+ screens. -->
  <NeutralContainer :padded="false" class="space-y-2 sm:space-y-3 p-1 sm:p-4">
    <!-- Month navigation -->
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

    <!-- Weekday header -->
    <div class="grid grid-cols-7 gap-0.5 sm:gap-1 text-[10px] sm:text-xs font-semibold text-(--text-muted)">
      <div v-for="d in weekdayHeader" :key="d" class="text-center py-1">{{ d }}</div>
    </div>

    <!-- Calendar grid: always 7 columns. Row count is 5 or 6 depending on whether the last
         row contains any current-month dates — see {@link cells}. -->
    <div class="grid grid-cols-7 gap-0.5 sm:gap-1">
      <div
          v-for="cell in cells"
          :key="cell.iso"
          class="min-h-16 sm:min-h-24 lg:min-h-28 border border-(--border) rounded p-0.5 sm:p-1.5 flex flex-col gap-0.5 overflow-hidden"
          :class="[
            cell.isCurrentMonth ? 'bg-(--bg)' : 'bg-(--bg-accent) opacity-50',
            cell.isToday ? 'ring-2 ring-primary' : '',
          ]"
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
        <div class="flex flex-col gap-0.5 overflow-hidden min-h-0">
          <EventChipButton
              v-for="evRef in cell.events.slice(0, 3)"
              :key="`${evRef.event.id}-${evRef.date}`"
              :title="`${evRef.event.name}${evRef.event.startTime ? ' · ' + formatTime(evRef.event.startTime) : ''}`"
              :custom-style="chipStyle(evRef.event)"
              @click="openEvent(evRef.event, evRef.date)"
          >
            <MutedIcon v-if="evRef.event.restricted" :icon="['fas', 'lock']" class="mr-0.5 inline" />
            <span class="hidden sm:inline">{{ formatTime(evRef.event.startTime) }}&nbsp;</span>
            <span>{{ evRef.event.name }}</span>
          </EventChipButton>
        </div>
      </div>
    </div>
  </NeutralContainer>
</template>
