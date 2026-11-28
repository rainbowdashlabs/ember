/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DeadlineAnchor, DeadlineKind, type RegistrationDeadline} from '@/api/generated/schema'
import {shiftIsoDate, stationDayOf} from '@/util/format'

export const MINUTES_PER_HOUR = 60
export const MINUTES_PER_DAY = 1440

/** The zone a computation runs in: the station's, or the reader's own where the station keeps none. */
export function zoneOrLocal(timeZone?: string | null): string {
    return timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone
}

/** Whether an instant lies in the past, false where there is none. */
export function hasPassed(instant: string | null | undefined, now: number = Date.now()): boolean {
    return !!instant && new Date(instant).getTime() <= now
}

/** A rule that never closes sign-up. */
export function noDeadline(): RegistrationDeadline {
    return {kind: DeadlineKind.NONE, at: null, offsetMinutes: null, anchor: null}
}

/**
 * How far the clock of a zone runs ahead of UTC at a moment, in milliseconds.
 *
 * <p>Read off what the zone's clock shows at that moment, which is what keeps it right on either side
 * of a change to or from summer time.
 */
function zoneOffsetAt(moment: number, timeZone: string): number {
    const parts = new Intl.DateTimeFormat('en-US', {
        timeZone,
        hourCycle: 'h23',
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
    }).formatToParts(new Date(moment))
    const value = (type: Intl.DateTimeFormatPartTypes) => Number(parts.find(part => part.type === type)?.value ?? 0)
    const wallClock = Date.UTC(value('year'), value('month') - 1, value('day'), value('hour'), value('minute'), value('second'))
    return wallClock - Math.floor(moment / 1000) * 1000
}

/**
 * The moment the midnight that begins a calendar day falls on in a time zone.
 *
 * <p>Midnight is tried with the offset in force a day before and a day after, so a change to or from
 * summer time on either side of it is handled. Where both readings hold, the earlier one is the start
 * of the day. Where neither does, midnight fell into the hour the clock skipped and the day begins
 * when the clock jumped, as the server counts it too.
 *
 * @param day      the calendar date, `yyyy-MM-dd`
 * @param timeZone the IANA zone whose midnight is meant
 */
function startOfDayIn(day: string, timeZone: string): Date {
    const wallMidnight = new Date(`${day}T00:00:00Z`).getTime()
    const before = wallMidnight - zoneOffsetAt(wallMidnight - 86_400_000, timeZone)
    const after = wallMidnight - zoneOffsetAt(wallMidnight + 86_400_000, timeZone)
    const holds = [before, after].filter(moment => moment + zoneOffsetAt(moment, timeZone) === wallMidnight)
    return new Date(holds.length > 0 ? Math.min(...holds) : before)
}

/** The midnight that begins the reader's own day of a moment, for a zone no runtime knows. */
function startOfLocalDay(moment: Date): Date {
    const midnight = new Date(moment)
    midnight.setHours(0, 0, 0, 0)
    return midnight
}

/**
 * The midnight that begins the day a moment falls on in the station's zone, moved back by whole
 * calendar days.
 *
 * <p>Falls back to the reader's own day for a zone no runtime knows, as {@link stationDayOf} does.
 *
 * @param daysBack the calendar days to go back from the moment's day
 */
function stationMidnightOf(moment: Date, timeZone: string, daysBack = 0): Date {
    try {
        return startOfDayIn(shiftIsoDate(stationDayOf(moment, timeZone), -daysBack), timeZone)
    } catch {
        const midnight = startOfLocalDay(moment)
        midnight.setDate(midnight.getDate() - daysBack)
        return midnight
    }
}

/**
 * When sign-up closes for the date starting at a moment, mirroring the server's computation.
 *
 * <p>A fixed deadline is the same moment for every date. A relative one is the span before the start.
 * With `MIDNIGHT` a span of whole days counts back calendar days on the station's clock from the day
 * of the start and closes at the midnight that begins that day: seven days before a Saturday at ten is
 * the Saturday a week earlier at midnight, also across a change to or from summer time. Any other span
 * is taken off the start and then goes down to the midnight that begins that day.
 *
 * @param rule            the deadline rule
 * @param occurrenceStart the start of the date
 * @param timeZone        the station's IANA zone, which shows where midnight is
 * @returns the closing moment, or null when sign-up does not close or the rule is incomplete
 */
export function closesAt(rule: RegistrationDeadline, occurrenceStart: Date | string, timeZone: string): Date | null {
    if (rule.kind === DeadlineKind.FIXED) return rule.at ? new Date(rule.at) : null
    if (rule.kind !== DeadlineKind.RELATIVE || rule.offsetMinutes == null) return null
    const start = new Date(occurrenceStart)
    if (Number.isNaN(start.getTime())) return null
    if (rule.anchor === DeadlineAnchor.MIDNIGHT && rule.offsetMinutes % MINUTES_PER_DAY === 0) {
        return stationMidnightOf(start, timeZone, rule.offsetMinutes / MINUTES_PER_DAY)
    }
    const shifted = new Date(start.getTime() - rule.offsetMinutes * 60_000)
    return rule.anchor === DeadlineAnchor.MIDNIGHT ? stationMidnightOf(shifted, timeZone) : shifted
}

/**
 * A rule as the server sends it, with every field it leaves out read as empty, and no rule at all read
 * as no deadline.
 */
export function deadlineRuleOf(value: Partial<RegistrationDeadline> | null | undefined): RegistrationDeadline {
    if (!value?.kind) return noDeadline()
    return {kind: value.kind, at: value.at ?? null, offsetMinutes: value.offsetMinutes ?? null, anchor: value.anchor ?? null}
}

/** Whether two rules mean the same, whatever objects hold them. */
export function sameRule(a: RegistrationDeadline | null, b: RegistrationDeadline | null): boolean {
    if (!a || !b) return a === b
    return a.kind === b.kind && a.at === b.at && a.offsetMinutes === b.offsetMinutes && a.anchor === b.anchor
}

/**
 * The rule a copy of an appointment takes over. A span before the start goes with the copy unchanged;
 * a fixed moment belonged to the original's dates and is dropped with them.
 */
export function deadlineForCopy(value: Partial<RegistrationDeadline> | null | undefined): RegistrationDeadline {
    const rule = deadlineRuleOf(value)
    return rule.kind === DeadlineKind.FIXED ? noDeadline() : rule
}
