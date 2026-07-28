/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import type {EventBreak, EventCategory, StationEvent} from '@/api/events'
import {EventTypes, isRecurringEvent} from '@/api/events'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CalendarDayCell from '@/views/stationview/events/upcomingview/eventscalendar/CalendarDayCell.vue'
import CalendarMultiDayBar from '@/views/stationview/events/upcomingview/eventscalendar/CalendarMultiDayBar.vue'
import {contrastingTextColorForHex} from '@/util/contrastColor'
import {formatTime} from '@/util/format'

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
const viewMonth = ref(today.getMonth())

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

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
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

function singleDayEventsForDate(date: Date): {event: StationEvent; date: string}[] {
  const dateStr = isoDate(date)
  const inBreak = props.eventBreaks.some(
      b => b.startDate && b.endDate && dateStr >= b.startDate && dateStr <= b.endDate,
  )
  if (inBreak) return []

  const result: {event: StationEvent; date: string}[] = []
  for (const ev of props.allEvents) {
    if (!isRelevant(ev.id) || !matchesFilters(ev)) continue
    if (isMultiDayEvent(ev)) continue

    if (ev.eventType === EventTypes.ONE_TIME) {
      if (!ev.startTime) continue
      const startStr = new Date(ev.startTime).toISOString().slice(0, 10)
      if (dateStr === startStr) result.push({event: ev, date: dateStr})
      continue
    }
    if (recurringOccurrenceStartsOn(ev, date)) result.push({event: ev, date: dateStr})
  }
  return result.sort((a, b) => (a.event.startTime ?? '').localeCompare(b.event.startTime ?? ''))
}

interface MultiDayBar {
  event: StationEvent
  startCol: number
  endCol: number
  lane: number
  continuesLeft: boolean
  continuesRight: boolean
  startIso: string
}

function clampedBar(
    weekDays: DayCell[],
    weekStart: string,
    weekEnd: string,
    ev: StationEvent,
    startIso: string,
    endIso: string,
): MultiDayBar | null {
  if (endIso < weekStart || startIso > weekEnd) return null
  const clampStart = startIso < weekStart ? weekStart : startIso
  const clampEnd = endIso > weekEnd ? weekEnd : endIso
  const startCol = weekDays.findIndex(c => c.iso === clampStart) + 1
  const endCol = weekDays.findIndex(c => c.iso === clampEnd) + 1
  if (startCol < 1 || endCol < 1) return null
  const blocked = weekDays.slice(startCol - 1, endCol).every(c => {
    return props.eventBreaks.some(
        b => b.startDate && b.endDate && c.iso >= b.startDate && c.iso <= b.endDate)
  })
  if (blocked) return null
  return {
    event: ev,
    startCol,
    endCol,
    lane: 0,
    continuesLeft: startIso < weekStart,
    continuesRight: endIso > weekEnd,
    startIso,
  }
}

function barStartDates(ev: StationEvent, weekStart: string, weekEnd: string, dur: number): string[] {
  if (ev.eventType === EventTypes.ONE_TIME) {
    if (!ev.startTime) return []
    return [new Date(ev.startTime).toISOString().slice(0, 10)]
  }
  if (!isRecurringEvent(ev.eventType)) return []
  const starts: string[] = []
  let cursor = new Date(addDays(weekStart, -dur) + 'T12:00:00')
  const stop = new Date(weekEnd + 'T12:00:00')
  while (cursor.getTime() <= stop.getTime()) {
    if (recurringOccurrenceStartsOn(ev, cursor)) starts.push(isoDate(cursor))
    cursor = new Date(cursor.getTime() + 24 * 3600 * 1000)
  }
  return starts
}

function assignLanes(bars: MultiDayBar[]) {
  bars.sort((a, b) => a.startCol - b.startCol || a.endCol - b.endCol)
  const laneEnds: number[] = []
  for (const bar of bars) {
    let lane = 0
    while (lane < laneEnds.length && laneEnds[lane]! >= bar.startCol) lane++
    laneEnds[lane] = bar.endCol
    bar.lane = lane
  }
}

function buildMultiDayBars(weekDays: DayCell[]): MultiDayBar[] {
  const weekStart = weekDays[0]?.iso
  const weekEnd = weekDays[weekDays.length - 1]?.iso
  if (!weekStart || !weekEnd) return []
  const bars: MultiDayBar[] = []

  for (const ev of props.allEvents) {
    if (!isRelevant(ev.id) || !matchesFilters(ev)) continue
    const dur = eventDayDuration(ev)
    if (dur < 1) continue
    for (const startIso of barStartDates(ev, weekStart, weekEnd, dur)) {
      const bar = clampedBar(weekDays, weekStart, weekEnd, ev, startIso, addDays(startIso, dur))
      if (bar) bars.push(bar)
    }
  }

  assignLanes(bars)
  return bars
}

interface Week {
  days: DayCell[]
  bars: MultiDayBar[]
  laneCount: number
}

const weeks = computed((): Week[] => {
  const firstOfMonth = new Date(viewYear.value, viewMonth.value, 1)
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
