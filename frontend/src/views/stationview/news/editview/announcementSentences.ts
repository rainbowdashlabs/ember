/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {DeadlineAnchor, DeadlineKind, EventType, type DateDeadlines, type StationEvent} from '@/api/generated/schema'
import {formatDateTime, formatWeekdayDate, shiftIsoDate, stationDayOf} from '@/util/format'
import {MINUTES_PER_DAY, MINUTES_PER_HOUR} from '@/views/stationview/events/eventshared/registrationDeadline'

/** The sentences an announcement can say about an event, each filled from one of its settings. */
export type AnnouncementSentence =
    | 'cancelled'
    | 'cancelledBecause'
    | 'registrationClosesOn'
    | 'registrationClosesBefore'
    | 'registrationClosesBeforeAtMidnight'
    | 'spanDays'
    | 'spanDaysOne'
    | 'spanHours'
    | 'spanHoursOne'
    | 'registrationRequired'
    | 'registrationLimit'
    | 'registrationConfirmation'
    | 'minimumRegistrations'
    | 'minimumRegistrationsBy'
    | 'minimumRegistrationsDaysBefore'

/** Writes one sentence in the reader's language, handed in rather than looked up so this stays testable. */
export type Say = (sentence: AnnouncementSentence, values?: Record<string, string | number>) => string

/**
 * What an event block does not show, written out as sentences: whether it is off, and how signing up
 * works. Each sentence stands only where the setting behind it is set, so an event without
 * registration says nothing about registering.
 *
 * <p>A called-off event says so first, with its reason where one was given, and nothing about
 * signing up for something that no longer takes place.
 *
 * <p>The deadline is named as the moment the server works out for the date of {@link deadlineDay}.
 * Only a series announced without a date, whose relative deadline falls differently for every date,
 * has it said as the rule itself. The minimum is checked at midnight at the start of the day the
 * server names, so the last day it can be reached on is the day before that one.
 *
 * @param event     the event being announced
 * @param deadlines when sign-up for that date closes and when its minimum is checked, null where
 *                  there is no such date or the server could not be asked
 * @param say       writes one sentence
 * @param timezone  the station's clock, which the moments are written on
 */
export function announcementSentences(
    event: StationEvent,
    deadlines: DateDeadlines | null,
    say: Say,
    timezone?: string | null,
): string[] {
    if (event.cancelled) {
        return [event.cancelReason?.trim() ? say('cancelledBecause', {reason: event.cancelReason.trim()}) : say('cancelled')]
    }
    if (!event.requiresRegistration) return []
    return [
        registration(event, deadlines, say, timezone),
        event.registrationLimit ? say('registrationLimit', {count: event.registrationLimit}) : '',
        event.requiresConfirmation ? say('registrationConfirmation') : '',
        minimum(event, deadlines, say, timezone),
    ].filter(sentence => sentence !== '')
}

/**
 * The date an announcement names its deadlines for: the announced date, or for a one-off appointment
 * or a fixed deadline the appointment's own first day. Null for a series announced without a date,
 * whose deadline falls differently for every date.
 *
 * @param event    the event being announced
 * @param date     the occurrence being announced, null where none was chosen
 * @param timezone the station's clock, which names the day the appointment starts on
 */
export function deadlineDay(event: StationEvent, date: string | null, timezone?: string | null): string | null {
    if (date) return date
    const onOwnDate = event.eventType === EventType.ONE_TIME || event.registrationDeadline.kind === DeadlineKind.FIXED
    return onOwnDate ? stationDayOf(new Date(event.startTime), timezone) : null
}

/**
 * That signing up is needed, and until when: the moment the server names for the date, a fixed
 * deadline's own moment where the server gave none, and the rule in words for a series announced
 * without a date.
 */
function registration(event: StationEvent, deadlines: DateDeadlines | null, say: Say, timezone?: string | null): string {
    const rule = event.registrationDeadline
    if (rule.kind === DeadlineKind.NONE) return say('registrationRequired')
    const closes = deadlines?.registrationClosesAt ?? (rule.kind === DeadlineKind.FIXED ? rule.at : null)
    if (closes) return say('registrationClosesOn', {date: formatDateTime(closes, timezone)})
    if (rule.kind !== DeadlineKind.RELATIVE || deadlines) return say('registrationRequired')
    const span = spanOf(rule.offsetMinutes ?? 0, say)
    return rule.anchor === DeadlineAnchor.MIDNIGHT
        ? say('registrationClosesBeforeAtMidnight', {span})
        : say('registrationClosesBefore', {span})
}

/** A span before the start in words, in whole days where it is some, in hours otherwise. */
function spanOf(minutes: number, say: Say): string {
    if (minutes > 0 && minutes % MINUTES_PER_DAY === 0) {
        const days = minutes / MINUTES_PER_DAY
        return days === 1 ? say('spanDaysOne') : say('spanDays', {count: days})
    }
    const hours = minutes / MINUTES_PER_HOUR
    return hours === 1 ? say('spanHoursOne') : say('spanHours', {count: hours})
}

/**
 * How many sign-ups the date needs, and by when. The check runs at the midnight that begins the day
 * the server names, so the sentence names the station's day before it as the last one to sign up on.
 */
function minimum(event: StationEvent, deadlines: DateDeadlines | null, say: Say, timezone?: string | null): string {
    const count = event.minRegistrations
    if (!count) return ''
    const days = event.thresholdDays
    if (days == null) return say('minimumRegistrations', {count})
    const checkedAt = deadlines?.minimumCheckedAt
    if (!checkedAt) return say('minimumRegistrationsDaysBefore', {count, days})
    const lastDay = shiftIsoDate(stationDayOf(new Date(checkedAt), timezone), -1)
    return say('minimumRegistrationsBy', {count, date: formatWeekdayDate(lastDay)})
}
