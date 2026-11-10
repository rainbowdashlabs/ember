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
