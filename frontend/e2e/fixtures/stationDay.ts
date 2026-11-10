/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/** The clock every seeded station keeps, and the one the browsers of the suite run on. */
export const STATION_TIMEZONE = 'Europe/Berlin'

/**
 * The calendar date a moment falls on at the seeded stations, as `yyyy-MM-dd`.
 *
 * <p>The server files an appointment's sign-ups, procedures and lendings under the day it falls on
 * where the station stands. The date part of the stored moment is the day in UTC, which between
 * ten and midnight UTC is the day before, so a story that cut it off failed every night.
 *
 * @param moment the moment, now when left out
 */
export function stationDay(moment: Date = new Date()): string {
    return new Intl.DateTimeFormat('en-CA', {
        timeZone: STATION_TIMEZONE,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
    }).format(moment)
}

/**
 * The moment the station's clock shows a time of day, a number of days from today.
 *
 * <p>A story that sets the hour on its own clock gets a moment the browser, running on the station's
 * clock, shows an hour or two off wherever the runner keeps UTC.
 *
 * @param daysAhead how many days from today on the station's calendar
 * @param hours     the hour on the station's clock
 * @param minutes   the minute
 */
export function stationMoment(daysAhead: number, hours: number, minutes = 0): Date {
    const day = stationDay(new Date(Date.now() + daysAhead * 86_400_000))
    const wanted = Date.parse(`${day}T${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:00Z`)
    return new Date(wanted - offsetAt(wanted - offsetAt(wanted)))
}

/** How far the station's clock is ahead of UTC at a moment, in milliseconds. */
function offsetAt(moment: number): number {
    const parts = Object.fromEntries(new Intl.DateTimeFormat('en-US', {
        timeZone: STATION_TIMEZONE,
        hourCycle: 'h23',
        year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
    }).formatToParts(new Date(moment)).map(part => [part.type, part.value]))
    const shown = Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day),
        Number(parts.hour), Number(parts.minute), Number(parts.second))
    return shown - moment
}
