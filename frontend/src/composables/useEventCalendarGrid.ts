/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { EventTypes, isRecurringEvent, type EventBreak, type StationEvent } from '@/api/events'

/**
 * One cell of the month grid. Cells outside the visible month are still rendered so the grid
 * keeps its shape, and carry {@code isCurrentMonth: false} so the view can dim them.
 */
export interface DayCell {
  date: Date
  iso: string
  isCurrentMonth: boolean
  isToday: boolean
  events: {event: StationEvent; date: string}[]
}

/**
 * A multi-day event drawn as a bar spanning columns of one week row. A bar that leaves the row
 * is clamped to it and flagged so the view can render an open edge.
 */
export interface MultiDayBar {
  event: StationEvent
  startCol: number
  endCol: number
  lane: number
  continuesLeft: boolean
  continuesRight: boolean
  startIso: string
}

export interface Week {
  days: DayCell[]
  bars: MultiDayBar[]
  laneCount: number
}

export interface CalendarSource {
  allEvents: StationEvent[]
  eventBreaks: EventBreak[]
  selectedCategoryId: string
  searchQuery: string
}

function pad2(n: number): string {
  return String(n).padStart(2, '0')
}

/**
 * The local calendar date of {@code d} as {@code yyyy-MM-dd}. Deliberately not
 * {@code toISOString()}, which would shift the date across the UTC boundary for evening events.
 */
function isoDate(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
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

/**
 * Builds the month grid the calendar renders: which events fall on which day, which multi-day
 * events span which columns, and which month is in view.
 *
 * Occurrences are unrolled in the browser from the full event list rather than requested per
 * month - one-time events match their start date, recurring patterns are derived from
 * {@code dayOfWeek} and the date's position in the month or year, and event breaks blank out
 * their range.
 */
export function useEventCalendarGrid(source: Ref<CalendarSource>) {
  const today = new Date()
  const todayIso = isoDate(today)
  const viewYear = ref(today.getFullYear())
  const viewMonth = ref(today.getMonth())

  function matchesFilters(ev: StationEvent): boolean {
    const {selectedCategoryId, searchQuery} = source.value
    if (selectedCategoryId && ev.categoryId !== Number(selectedCategoryId)) return false
    if (!searchQuery) return true
    const q = searchQuery.toLowerCase()
    return !!(ev.name?.toLowerCase().includes(q) || ev.description?.toLowerCase().includes(q))
  }

  /**
   * Whether an appointment belongs in the grid.
   *
   * <p>Only the reader's own filters decide that. Who may know an appointment exists is settled by
   * the server, which does not hand over what it hides, and an appointment somebody may see but not
   * answer belongs in the calendar as much as any other.
   */
  function isVisible(ev: StationEvent): boolean {
    return matchesFilters(ev)
  }

  function inBreak(iso: string): boolean {
    return source.value.eventBreaks.some(
        b => b.startDate && b.endDate && iso >= b.startDate && iso <= b.endDate,
    )
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
    if (inBreak(dateStr)) return []

    const result: {event: StationEvent; date: string}[] = []
    for (const ev of source.value.allEvents) {
      if (!isVisible(ev) || eventDayDuration(ev) > 0) continue

      if (ev.eventType === EventTypes.ONE_TIME) {
        if (!ev.startTime) continue
        if (dateStr === new Date(ev.startTime).toISOString().slice(0, 10)) {
          result.push({event: ev, date: dateStr})
        }
        continue
      }
      if (recurringOccurrenceStartsOn(ev, date)) result.push({event: ev, date: dateStr})
    }
    return result.sort((a, b) => (a.event.startTime ?? '').localeCompare(b.event.startTime ?? ''))
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
    if (weekDays.slice(startCol - 1, endCol).every(c => inBreak(c.iso))) return null
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

  /**
   * Stacks overlapping bars so none is drawn on top of another; each takes the first lane whose
   * previous bar has already ended.
   */
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

    for (const ev of source.value.allEvents) {
      if (!isVisible(ev)) continue
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

  /**
   * The visible month as week rows. Six rows are laid out so the grid height is stable, and the
   * trailing row is dropped when it belongs entirely to the next month.
   */
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
      result.push({days, bars, laneCount: bars.reduce((m, b) => Math.max(m, b.lane + 1), 0)})
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

  return {viewYear, viewMonth, weeks, prevMonth, nextMonth, goToToday}
}
