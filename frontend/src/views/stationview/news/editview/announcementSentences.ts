/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {StationEvent} from '@/api/events'
import {formatDateTime, formatWeekdayDate} from '@/util/format'

/** The sentences an announcement can say about an event, each filled from one of its settings. */
export type AnnouncementSentence =
    | 'cancelled'
    | 'cancelledBecause'
    | 'registrationClosesOn'
    | 'registrationRequired'
    | 'registrationLimit'
    | 'registrationConfirmation'
    | 'unconfirmedLapseOn'
    | 'unconfirmedLapseDaysBefore'
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
 * <p>The days-before setting does not close registration. On that day the station declines every
 * sign-up still waiting for confirmation, so it is only mentioned where sign-ups are confirmed, and
 * said as what it does. The minimum of registrations is counted back from the occurrence the same
 * way, since it has to be reached a number of days before each date.
 *
 * @param event    the event being announced
 * @param date     the occurrence being announced, which the days-before setting is counted back from
 * @param say      writes one sentence
 * @param timezone the station's clock, which the moments are written on
 */
export function announcementSentences(
    event: StationEvent,
    date: string | null,
    say: Say,
    timezone?: string | null,
): string[] {
    if (event.cancelled) {
        return [event.cancelReason?.trim() ? say('cancelledBecause', {reason: event.cancelReason.trim()}) : say('cancelled')]
    }
    if (!event.requiresRegistration) return []
    return [
        event.registrationDeadline
            ? say('registrationClosesOn', {date: formatDateTime(event.registrationDeadline, timezone)})
            : say('registrationRequired'),
        event.registrationLimit ? say('registrationLimit', {count: event.registrationLimit}) : '',
        event.requiresConfirmation ? say('registrationConfirmation') : '',
        event.requiresConfirmation ? lapse(event, date, say) : '',
        minimum(event, date, say),
    ].filter(sentence => sentence !== '')
}

function lapse(event: StationEvent, date: string | null, say: Say): string {
    const days = event.registrationCloseDays
    if (!days) return ''
    if (!date) return say('unconfirmedLapseDaysBefore', {days})
    return say('unconfirmedLapseOn', {date: formatWeekdayDate(daysBefore(date, days))})
}

function minimum(event: StationEvent, date: string | null, say: Say): string {
    const count = event.minRegistrations
    if (!count) return ''
    const days = event.thresholdDays
    if (days == null) return say('minimumRegistrations', {count})
    if (!date) return say('minimumRegistrationsDaysBefore', {count, days})
    return say('minimumRegistrationsBy', {count, date: formatWeekdayDate(daysBefore(date, days))})
}

/** The calendar day a number of days before another, both as `YYYY-MM-DD`. */
function daysBefore(date: string, days: number): string {
    const day = new Date(`${date}T00:00:00Z`)
    day.setUTCDate(day.getUTCDate() - days)
    return day.toISOString().slice(0, 10)
}
