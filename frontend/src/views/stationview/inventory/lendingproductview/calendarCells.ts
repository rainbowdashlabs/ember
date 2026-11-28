/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {RemoteDay, RemoteDayCount} from '@/api/generated/schema'

/** How much of what is looked at is free on a day. */
export type DayState = 'all' | 'some' | 'none' | 'blocked'

/** One cell of a month grid: a day, or an empty place before the first day. */
export type CalendarCell =
    | {kind: 'blank'; key: string}
    | {kind: 'day'; key: string; day: string; number: number; state: DayState; counts: RemoteDayCount[]}

/**
 * Which calendar row a count belongs to: `all` for every row, `singles` for the single pieces
 * together, `piece` for one piece, otherwise the kind's id or `copies` for an inventory of copies.
 */
export function rowKey(count: RemoteDayCount): string {
    if (count.singles) return 'singles'
    if (count.itemId != null) return 'piece'
    return count.artId == null ? 'copies' : String(count.artId)
}

/** How much of the chosen rows is free on a day, a blocked day being none whatever its counts. */
export function stateOf(day: RemoteDay, choice: string): DayState {
    if (day.blocked) return 'blocked'
    const counts = day.counts.filter(count => choice === 'all' || rowKey(count) === choice)
    const free = counts.reduce((sum, count) => sum + count.free, 0)
    const offered = counts.reduce((sum, count) => sum + count.offered, 0)
    if (free === 0) return 'none'
    return free >= offered ? 'all' : 'some'
}

/**
 * The cells of a month grid, weeks starting on Monday, with an empty cell for every weekday before the
 * first day.
 */
export function monthCells(days: RemoteDay[], choice: string): CalendarCell[] {
    const cells: CalendarCell[] = []
    const start = days[0]
    if (!start) return cells
    const first = new Date(`${start.day}T00:00:00Z`)
    const leading = (first.getUTCDay() + 6) % 7
    for (let index = 0; index < leading; index++) cells.push({kind: 'blank', key: `blank-${index}`})
    for (const day of days) {
        cells.push({
            kind: 'day',
            key: day.day,
            day: day.day,
            number: Number(day.day.slice(8, 10)),
            state: stateOf(day, choice),
            counts: day.counts,
        })
    }
    return cells
}

/**
 * The days chosen after a click: the first click sets the first day, the second the last one; a
 * click before the first day, or once both are set, starts again.
 */
export function pickDay(day: string, from: string, to: string): {from: string; to: string} {
    if (!from || to) return {from: day, to: ''}
    if (day < from) return {from: day, to: ''}
    return {from, to: day}
}

/** Whether a day lies in the chosen days; a first day alone is a one-day choice. */
export function inChosen(day: string, from: string, to: string): boolean {
    if (!from) return false
    return day >= from && day <= (to || from)
}

/** The month a date falls in, as `yyyy-MM`. */
export function monthOf(date: string): string {
    return date.slice(0, 7)
}

/** The month `by` months after a `yyyy-MM` month, or before it for a negative `by`. */
export function shiftMonth(month: string, by: number): string {
    const [year = 1970, number = 1] = month.split('-').map(Number)
    const shifted = new Date(Date.UTC(year, number - 1 + by, 1))
    return `${shifted.getUTCFullYear()}-${String(shifted.getUTCMonth() + 1).padStart(2, '0')}`
}
