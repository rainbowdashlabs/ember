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

// For any event (one-time or recurring), the number of additional calendar days the
// occurrence spans beyond the start. 0 = single day, 1 = spans 2 days, etc. Negative diffs
// (corrupted data, or a series cut-off that landed before start) are clamped to 0.
function eventDayDuration(ev: StationEvent): number {
  if (!ev.startTime || !ev.endTime) return 0
  const startDay = new Date(ev.startTime).toISOString().slice(0, 10)
  const endDay = new Date(ev.endTime).toISOString().slice(0, 10)
  if (endDay <= startDay) return 0
  return Math.round(
      (Date.UTC(...isoToYmd(endDay)) - Date.UTC(...isoToYmd(startDay))) / (24 * 3600 * 1000),
  )
}

function isMultiDayEvent(ev: StationEvent): boolean {
  return eventDayDuration(ev) > 0
}

function isoToYmd(iso: string): [number, number, number] {
  const [y, m, d] = iso.split('-').map(Number) as [number, number, number]
  return [y, m - 1, d]
}

function addDays(iso: string, days: number): string {
  const [y, m, d] = isoToYmd(iso)
  const t = new Date(Date.UTC(y, m, d))
  t.setUTCDate(t.getUTCDate() + days)
  return t.toISOString().slice(0, 10)
}

// True if the date (in local time) is a valid occurrence start for the given recurring event.
function recurringOccurrenceStartsOn(ev: StationEvent, date: Date): boolean {
  if (!isRecurringEvent(ev.eventType)) return false
  const dow = date.getDay() === 0 ? 7 : date.getDay()
  if (!ev.dayOfWeek || ev.dayOfWeek !== dow) return false
  const dayOfMonth = date.getDate()
  const month = date.getMonth()
  if (ev.eventType === EventTypes.RECURRING) return true
  if (ev.eventType === EventTypes.MONTHLY_FIRST) return dayOfMonth <= 7
  if (ev.eventType === EventTypes.QUARTERLY) return dayOfMonth <= 7 && month % 3 === 0
  if (ev.eventType === EventTypes.YEARLY && ev.startTime) {
    const ref = new Date(ev.startTime)
    return ref.getMonth() === month && ref.getDate() === dayOfMonth
  }
  return false
}

// Single-day chips that live INSIDE a day cell. Multi-day occurrences are excluded — they
// are rendered as spanning bars by `weeks`.
function singleDayEventsForDate(date: Date): {event: StationEvent; date: string}[] {
  const dateStr = isoDate(date)
  const inBreak = props.eventBreaks.some(
      b => b.startDate && b.endDate && dateStr >= b.startDate && dateStr <= b.endDate,
  )
  if (inBreak) return []

  const result: {event: StationEvent; date: string}[] = []
  for (const ev of props.allEvents) {
    if (!isRelevant(ev.id) || !matchesFilters(ev)) continue
    if (isMultiDayEvent(ev)) continue // handled by the spanning bars layer

    if (ev.eventType === EventTypes.ONE_TIME) {
      if (!ev.startTime) continue
      const startStr = new Date(ev.startTime).toISOString().slice(0, 10)
      if (dateStr === startStr) result.push({event: ev, date: dateStr})
      continue
    }
    if (recurringOccurrenceStartsOn(ev, date)) result.push({event: ev, date: dateStr})
  }
  // Sort by start time within each day so the cell reads top-down chronologically.
  return result.sort((a, b) => (a.event.startTime ?? '').localeCompare(b.event.startTime ?? ''))
}

interface MultiDayBar {
  event: StationEvent
  startCol: number      // 1-based column (Mon=1 .. Sun=7) where the bar starts in the week
  endCol: number        // 1-based column where the bar ends (inclusive)
  lane: number          // 0-based vertical lane within the week
  continuesLeft: boolean  // event started before this week
  continuesRight: boolean // event ends after this week
  startIso: string      // for click-to-detail
}

// Builds one bar per (multi-day occurrence × intersecting week) for the given week. Includes
// both one-time and recurring multi-day events; recurring events are enumerated by stepping
// each candidate start date in [weekStart - duration, weekEnd] and checking the recurrence
// rule. Lanes are packed greedily so no two bars in the same week overlap.
function buildMultiDayBars(weekDays: DayCell[]): MultiDayBar[] {
  if (weekDays.length === 0) return []
  const weekStart = weekDays[0].iso
  const weekEnd = weekDays[6].iso
  const bars: MultiDayBar[] = []

  const pushBar = (ev: StationEvent, startIso: string, endIso: string) => {
    if (endIso < weekStart || startIso > weekEnd) return
    const clampStart = startIso < weekStart ? weekStart : startIso
    const clampEnd = endIso > weekEnd ? weekEnd : endIso
    const startCol = weekDays.findIndex(c => c.iso === clampStart) + 1
    const endCol = weekDays.findIndex(c => c.iso === clampEnd) + 1
    if (startCol < 1 || endCol < 1) return
    // Skip if every covered day is inside an event break.
    const blocked = weekDays.slice(startCol - 1, endCol).every(c => {
      return props.eventBreaks.some(
          b => b.startDate && b.endDate && c.iso >= b.startDate && c.iso <= b.endDate)
    })
    if (blocked) return
    bars.push({
      event: ev,
      startCol,
      endCol,
      lane: 0,
      continuesLeft: startIso < weekStart,
      continuesRight: endIso > weekEnd,
      startIso,
    })
  }

  for (const ev of props.allEvents) {
    if (!isRelevant(ev.id) || !matchesFilters(ev)) continue
    const dur = eventDayDuration(ev)
    if (dur < 1) continue

    if (ev.eventType === EventTypes.ONE_TIME) {
      if (!ev.startTime) continue
      const startIso = new Date(ev.startTime).toISOString().slice(0, 10)
      pushBar(ev, startIso, addDays(startIso, dur))
      continue
    }
    if (!isRecurringEvent(ev.eventType)) continue
    // Enumerate possible occurrence start dates that could still overlap this week —
    // anything starting up to `dur` days before the week's first day.
    const iterFrom = addDays(weekStart, -dur)
    const iterTo = weekEnd
    let cursor = new Date(iterFrom + 'T12:00:00')
    const stop = new Date(iterTo + 'T12:00:00')
    while (cursor.getTime() <= stop.getTime()) {
      if (recurringOccurrenceStartsOn(ev, cursor)) {
        const startIso = isoDate(cursor)
        pushBar(ev, startIso, addDays(startIso, dur))
      }
      cursor = new Date(cursor.getTime() + 24 * 3600 * 1000)
    }
  }

  // Lane packing: greedy by startCol. Lower lane number = higher up.
  bars.sort((a, b) => a.startCol - b.startCol || a.endCol - b.endCol)
  const laneEnds: number[] = []
  for (const bar of bars) {
    let lane = 0
    while (lane < laneEnds.length && laneEnds[lane]! >= bar.startCol) lane++
    laneEnds[lane] = bar.endCol
    bar.lane = lane
  }
  return bars
}

interface Week {
  days: DayCell[]
  bars: MultiDayBar[]
  laneCount: number
}

const weeks = computed((): Week[] => {
  const firstOfMonth = new Date(viewYear.value, viewMonth.value, 1)
  // Map JS Sunday-first weekday to Monday-first offset: Mon=0 .. Sun=6.
  const firstWeekdayMon = (firstOfMonth.getDay() + 6) % 7
  const allCells: DayCell[] = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(viewYear.value, viewMonth.value, 1 - firstWeekdayMon + i)
    const iso = isoDate(d)
    allCells.push({
      date: d,
      iso,
      isCurrentMonth: d.getMonth() === viewMonth.value && d.getFullYear() === viewYear.value,
      isToday: iso === todayIso,
      events: singleDayEventsForDate(d),
    })
  }
  // Trim a trailing row that's entirely next-month dates — keeps the grid compact without
  // exposing a ghost row.
  const lastRow = allCells.slice(35)
  const usedCells = lastRow.every(c => !c.isCurrentMonth) ? allCells.slice(0, 35) : allCells
  const result: Week[] = []
  for (let w = 0; w < usedCells.length; w += 7) {
    const days = usedCells.slice(w, w + 7)
    const bars = buildMultiDayBars(days)
    const laneCount = bars.reduce((m, b) => Math.max(m, b.lane + 1), 0)
    result.push({days, bars, laneCount})
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

    <!-- Per-week subgrids. Each week is its own 7-column grid so multi-day events can use
         `grid-column-start` / `grid-column-end` to span across cells (Google-calendar style).
         Day cells occupy `grid-row: 1 / -1` so they form the visual background; the
         multi-day bar rows are placed on top in lanes (rows 2..). -->
    <div class="space-y-0.5 sm:space-y-1">
      <div
          v-for="(week, weekIdx) in weeks"
          :key="weekIdx"
          class="grid grid-cols-7 gap-0.5 sm:gap-1"
          :style="{
            gridTemplateRows: `auto ${'1.25rem '.repeat(week.laneCount)}1fr`,
          }"
      >
        <!-- Day cells (background, day number top-left, single-day chips bottom) -->
        <div
            v-for="(cell, dayIdx) in week.days"
            :key="cell.iso"
            class="min-h-16 sm:min-h-24 lg:min-h-28 border border-(--border) rounded p-0.5 sm:p-1.5 flex flex-col gap-0.5 overflow-hidden"
            :class="[
              cell.isCurrentMonth ? 'bg-(--bg)' : 'bg-(--bg-accent) opacity-50',
              cell.isToday ? 'ring-2 ring-primary' : '',
            ]"
            :style="{gridColumn: dayIdx + 1, gridRow: `1 / span ${week.laneCount + 2}`}"
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
          <!-- Reserve vertical room for the multi-day bars overlaid above this cell so
               single-day chips don't visually collide with them. -->
          <div :style="{height: `${week.laneCount * 1.25}rem`}" aria-hidden="true"/>
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
        <!-- Multi-day bars (column-span, lane row). Rendered AFTER the cells so source
             order plus relative positioning puts them on top. -->
        <button
            v-for="(bar, barIdx) in week.bars"
            :key="`bar-${weekIdx}-${barIdx}`"
            type="button"
            class="relative z-10 text-left text-[10px] sm:text-xs leading-tight px-1 truncate cursor-pointer transition-opacity self-center"
            :class="[
              chipStyle(bar.event) ? 'hover:opacity-80' : 'bg-primary/30 hover:bg-primary/50 text-primary',
              bar.continuesLeft ? 'rounded-l-none ml-0' : 'rounded-l ml-0.5',
              bar.continuesRight ? 'rounded-r-none mr-0' : 'rounded-r mr-0.5',
            ]"
            :style="{
              gridColumnStart: bar.startCol,
              gridColumnEnd: bar.endCol + 1,
              gridRow: bar.lane + 2,
              ...(chipStyle(bar.event) ?? {}),
            }"
            :title="`${bar.event.name}${bar.event.startTime ? ' · ' + formatTime(bar.event.startTime) : ''}`"
            @click="openEvent(bar.event, bar.startIso)"
        >
          <font-awesome-icon v-if="bar.event.restricted" :icon="['fas', 'lock']" class="mr-0.5 inline h-2.5 w-2.5" />
          {{ bar.event.name }}
        </button>
      </div>
    </div>
  </NeutralContainer>
</template>
