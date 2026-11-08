/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {formatDaySpan, formatTime, formatWeekdayDate, stationDayOf} from '@/util/format'

/**
 * One occurrence of an event, written out: `Dienstag, 14.10.2026, 18:00 bis 20:00`.
 *
 * <p>A weekly event has no single date, so naming only the event says nothing about which evening
 * is meant. The day comes from the occurrence and the clock from the event, which keeps the same
 * hours on every day it repeats.
 *
 * @param date      the occurrence as `YYYY-MM-DD`
 * @param startTime the event's start, whose clock is used
 * @param endTime   the event's end, whose clock is used
 * @param until     the word between the two clocks
 * @param timezone  the clock to read the times on, the reader's own where none is named
 */
export function occurrenceLabel(
    date: string,
    startTime: string | null | undefined,
    endTime: string | null | undefined,
    until: string,
    timezone?: string | null,
): string {
    const day = formatWeekdayDate(date)
    const start = formatTime(startTime, timezone)
    const end = formatTime(endTime, timezone)
    if (!start) return day
    if (!end) return `${day}, ${start}`
    return `${day}, ${start} ${until} ${end}`
}

/**
 * A single appointment, written out from its start to its end: `Dienstag, 14.10.2026, 18:00 bis 20:00`.
 *
 * <p>The day is read where the appointment takes place. One that ends on a later day names both days
 * with their clocks, since the hours written once would read as though they held on every day between.
 *
 * @param startTime the moment it starts
 * @param endTime   the moment it ends, if it has one
 * @param until     the word between the two clocks
 * @param timezone  the clock to read the times on, the reader's own where none is named
 */
export function appointmentLabel(
    startTime: string,
    endTime: string | null | undefined,
    until: string,
    timezone?: string | null,
): string {
    const startDay = stationDayOf(new Date(startTime), timezone)
    const endDay = endTime ? stationDayOf(new Date(endTime), timezone) : startDay
    if (endDay === startDay) return occurrenceLabel(startDay, startTime, endTime, until, timezone)
    return formatDaySpan(startDay, formatTime(startTime, timezone), endDay, formatTime(endTime, timezone))
}
