/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {EventType} from '@/api/generated/schema'
import {shiftIsoDate, utcDayOf} from '@/util/format'

const DAY_MS = 86_400_000

/** A `yyyy-MM-dd` date as a moment at midnight UTC, which day arithmetic can step through without a clock change. */
function dayOf(isoDate: string): Date {
    return new Date(`${isoDate}T00:00:00Z`)
}

/** How many days lie from one day to another. */
function daysBetween(from: string, to: string): number {
    return Math.round((dayOf(to).getTime() - dayOf(from).getTime()) / DAY_MS)
}

/** The ISO weekday of a day, Monday being 1 and Sunday 7. */
function isoWeekday(isoDate: string): number {
    const day = dayOf(isoDate).getUTCDay()
    return day === 0 ? 7 : day
}

/** The first day on or after this one that falls on the weekday. */
function nextOrSameWeekday(isoDate: string, weekday: number): string {
    return shiftIsoDate(isoDate, (weekday - isoWeekday(isoDate) + 7) % 7)
}

/** The first day of a month that falls on the weekday, the month named by any of its days. */
function firstWeekdayOfMonth(isoDate: string, weekday: number): string {
    return nextOrSameWeekday(`${isoDate.slice(0, 8)}01`, weekday)
}

/** The first day of the month so many months after the month of a day. */
function monthsLater(isoDate: string, months: number): string {
    const day = dayOf(`${isoDate.slice(0, 8)}01`)
    day.setUTCMonth(day.getUTCMonth() + months)
    return utcDayOf(day)
}

/** How many months lie from the month of one day to the month of another. */
function monthsBetween(from: string, to: string): number {
    return (Number(to.slice(0, 4)) - Number(from.slice(0, 4))) * 12 + Number(to.slice(5, 7)) - Number(from.slice(5, 7))
}

/**
 * The first of the weekday in a month on or after `from`, counting only every `step`-th month from the
 * month of `first`, which keeps a quarterly series in the months it started in.
 */
function nextFirstWeekday(first: string, weekday: number, step: number, from: string): string {
    const month = monthsLater(from, ((-monthsBetween(first, from) % step) + step) % step)
    const candidate = firstWeekdayOfMonth(month, weekday)
    return candidate >= from ? candidate : firstWeekdayOfMonth(monthsLater(month, step), weekday)
}

/** Whether a year has a day of this month and day, which a 29 February does not every year. */
function existsIn(year: number, monthDay: string): boolean {
    return utcDayOf(dayOf(`${year}-${monthDay}`)) === `${year}-${monthDay}`
}

/** The first day on or after `from` that carries the month and day of `first`. */
function nextYearly(first: string, from: string): string {
    const monthDay = first.slice(5)
    for (let year = Number(from.slice(0, 4)); ; year++) {
        if (!existsIn(year, monthDay)) continue
        const candidate = `${year}-${monthDay}`
        if (candidate >= from) return candidate
    }
}

/**
 * The first day on or after today that a series repeating this way falls on, for a series that has
 * ended and so has no next date of its own to ask the server for.
 *
 * <p>The same pattern the server repeats by: weekly on the weekday, monthly on the first of that
 * weekday in a month, quarterly the same in every third month counted from the month it started in,
 * yearly on the day and month it started on.
 *
 * @param eventType how the series repeats
 * @param dayOfWeek the ISO weekday it falls on, or null for the weekday of its first day
 * @param firstDay  the day the series started on, as `yyyy-MM-dd`
 * @param today     today, as `yyyy-MM-dd`
 */
export function nextSeriesDay(eventType: string, dayOfWeek: number | null, firstDay: string, today: string): string {
    const weekday = dayOfWeek ?? isoWeekday(firstDay)
    switch (eventType) {
        case EventType.RECURRING:
            return nextOrSameWeekday(today, weekday)
        case EventType.MONTHLY_FIRST:
            return nextFirstWeekday(firstDay, weekday, 1, today)
        case EventType.QUARTERLY:
            return nextFirstWeekday(firstDay, weekday, 3, today)
        case EventType.YEARLY:
            return nextYearly(firstDay, today)
        default:
            return today
    }
}

/**
 * The day a copied series starts on: the original's next date where it still has one, which the
 * server works out from its own rule, its breaks and its called-off dates, and otherwise the next day
 * from today its pattern would fall on.
 *
 * @param eventType how the series repeats
 * @param dayOfWeek the ISO weekday it falls on, or null for the weekday of its first day
 * @param firstDay  the day the series started on, as `yyyy-MM-dd`
 * @param today     today, as `yyyy-MM-dd`
 * @param nextDate  the date the server names for the original, which is its last one once it ended
 */
export function copyStartDay(
    eventType: string,
    dayOfWeek: number | null,
    firstDay: string,
    today: string,
    nextDate: string | null,
): string {
    if (nextDate && nextDate >= today) return nextDate
    return nextSeriesDay(eventType, dayOfWeek, firstDay, today)
}

/**
 * The start and end of a copied series, placed on the day it starts on.
 *
 * <p>Both keep their time of day, and the end keeps its distance in days from the start, so an
 * evening stays the same evening and a weekend stays a weekend.
 *
 * @param start the original start, as a `datetime-local` value
 * @param end   the original end, as a `datetime-local` value
 * @param day   the day the copy starts on, as `yyyy-MM-dd`
 */
export function placedOn(start: string, end: string, day: string): {startTime: string; endTime: string} {
    const startTime = `${day}${start.slice(10)}`
    if (!end) return {startTime, endTime: ''}
    const endDay = shiftIsoDate(day, daysBetween(start.slice(0, 10), end.slice(0, 10)))
    return {startTime, endTime: `${endDay}${end.slice(10)}`}
}

/**
 * How long an occasion runs, in milliseconds, which a copied one-time appointment keeps for the date
 * entered next. Nothing where either time is missing or the end lies before the start.
 *
 * @param start the start, as a `datetime-local` value
 * @param end   the end, as a `datetime-local` value
 */
export function lengthOf(start: string, end: string): number | null {
    if (!start || !end) return null
    const length = new Date(end).getTime() - new Date(start).getTime()
    return Number.isFinite(length) && length >= 0 ? length : null
}
