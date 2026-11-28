/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {CalendarBar, CalendarBlock} from '@/api/generated/schema'
import {shiftIsoDate} from '@/util/format'

/** How many days the calendar shows at once: four weeks. */
export const CALENDAR_DAYS = 28

/** What a row of the calendar stands for: a product, or one of its kinds. */
export interface RowRef {
    inventoryId: number
    artId: number | null
    pieceCount: number
}

/** A bar laid out on a row: its columns and the lane it takes so bars never overlap. */
export interface PlacedBar {
    bar: CalendarBar
    start: number
    end: number
    lane: number
}

/** The days a window holds, as ISO dates. */
export function daysFrom(first: string, count = CALENDAR_DAYS): string[] {
    return Array.from({length: count}, (_, index) => shiftIsoDate(first, index))
}

/** The columns, counted from one, a span of days takes in the window, or null where it lies outside. */
export function columnsOf(from: string, to: string, days: readonly string[]): {start: number; end: number} | null {
    const first = days[0]
    const last = days.at(-1)
    if (!first || !last || to < first || from > last) return null
    const start = from <= first ? 1 : days.indexOf(from) + 1
    const end = to >= last ? days.length : days.indexOf(to) + 1
    return {start, end}
}

/** The bars of a row: a product's row takes every line of it, a kind's row the lines of that kind. */
export function barsOf(bars: readonly CalendarBar[], row: RowRef): CalendarBar[] {
    return bars.filter(bar => bar.inventoryId === row.inventoryId && (row.artId === null || bar.artId === row.artId))
}

/** The bars of a row placed in lanes, each bar in the first lane free over its days. */
export function placeBars(bars: readonly CalendarBar[], days: readonly string[]): PlacedBar[] {
    const lanes: number[] = []
    const placed: PlacedBar[] = []
    for (const bar of [...bars].sort((a, b) => a.from.localeCompare(b.from))) {
        const columns = columnsOf(bar.from, bar.to, days)
        if (!columns) continue
        let lane = lanes.findIndex(lastEnd => lastEnd < columns.start)
        if (lane === -1) lane = lanes.length
        lanes[lane] = columns.end
        placed.push({bar, ...columns, lane})
    }
    return placed
}

/** The statuses that hold pieces on a day: asked for, agreed, or out. */
const HOLDING: ReadonlySet<string> = new Set(['REQUESTED', 'APPROVED', 'LENT'])

/**
 * The days on which what is asked for, agreed or out exceeds the pieces of the row, summed per day.
 * A product's row also takes every day one of its kinds asks for more than that kind holds, so a
 * kind's shortage shows while its row is folded away.
 */
export function clashDays(bars: readonly CalendarBar[], row: RowRef, days: readonly string[], kinds: readonly RowRef[] = []): Set<string> {
    const clashes = new Set<string>()
    for (const day of days) {
        const held = barsOf(bars, row)
            .filter(bar => HOLDING.has(bar.status) && bar.from <= day && bar.to >= day)
            .reduce((sum, bar) => sum + bar.quantity, 0)
        if (held > row.pieceCount) clashes.add(day)
    }
    for (const kind of kinds) for (const day of clashDays(bars, kind, days)) clashes.add(day)
    return clashes
}

/**
 * The block periods drawn on a row: the station's, the product's, and those of its pieces, on a kind's
 * row only the pieces of that kind. A block on a piece that is gone is drawn nowhere.
 */
export function blocksOf(blocks: readonly CalendarBlock[], row: Pick<RowRef, 'inventoryId' | 'artId'>): CalendarBlock[] {
    return blocks.filter(block => {
        if (block.inventoryId === null) return block.itemId === null
        if (block.inventoryId !== row.inventoryId) return false
        return row.artId === null || block.itemId === null || block.artId === row.artId
    })
}

/** The days a drag covered, in order whichever way it went. */
export function dragSpan(from: string, to: string): {from: string; to: string} {
    return from <= to ? {from, to} : {from: to, to: from}
}
