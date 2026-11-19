/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {stationClock, stationDayOf} from '@/util/format'
import {DeadlineKind, type RegistrationDeadline} from '@/api/generated/schema'
import {closesAt, zoneOrLocal} from './registrationDeadline'

/** What the station's clock and calendar show at a moment, read as if it were UTC, to the minute. */
function wallClockOf(moment: Date, timeZone: string): number {
    return Date.parse(`${stationDayOf(moment, timeZone)}T${stationClock(moment, timeZone)}:00Z`)
}

/**
 * When the date of an appointment on a given day starts: the time of day of its first start, on
 * that day, on the station's clock.
 *
 * <p>The first start is moved by whole days and then corrected by however far the station's clock
 * moved in between, so a series running across a change to or from summer time keeps its hour.
 *
 * @param eventStart the start of the appointment's first date
 * @param day        the date asked about, `yyyy-MM-dd` on the station's calendar
 * @param timeZone   the station's IANA zone, or nothing to use the reader's own
 */
export function occurrenceStartOn(eventStart: string, day: string, timeZone?: string | null): Date {
    const zone = zoneOrLocal(timeZone)
    const first = new Date(eventStart)
    const firstWall = wallClockOf(first, zone)
    const days = Date.parse(`${day}T00:00:00Z`) - Date.parse(`${stationDayOf(first, zone)}T00:00:00Z`)
    const targetWall = firstWall + days
    const guess = first.getTime() + days
    return new Date(guess + targetWall - wallClockOf(new Date(guess), zone))
}

/**
 * When sign-up for one date of an appointment closes, for screens that know the rule and the date
 * but were not sent the instant.
 *
 * <p>A fixed deadline closes every date of a series at once; a relative one closes each date on its own.
 *
 * @returns the closing instant as an ISO string, or null where sign-up does not close before the date
 */
export function closesOnDate(
    rule: RegistrationDeadline | null | undefined,
    eventStart: string,
    day: string,
    timeZone?: string | null,
): string | null {
    if (!rule || rule.kind === DeadlineKind.NONE) return null
    const start = occurrenceStartOn(eventStart, day, timeZone)
    if (Number.isNaN(start.getTime())) return null
    return closesAt(rule, start, zoneOrLocal(timeZone))?.toISOString() ?? null
}

/** Whether an instant lies in the past, false where there is none. */
export function hasPassed(instant: string | null | undefined, now: number = Date.now()): boolean {
    return !!instant && new Date(instant).getTime() <= now
}
