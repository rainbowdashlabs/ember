/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {StationEvent} from '@/api/events'
import {formatDate, formatDateTime, formatWeekdayDate} from '@/util/format'

/** The sentences an announcement can say about an event, each filled from one of its settings. */
export type AnnouncementSentence =
    | 'cancelled'
    | 'cancelledBecause'
    | 'registrationClosesOn'
    | 'registrationClosesDaysBefore'
    | 'registrationRequired'
    | 'registrationLimit'
    | 'registrationConfirmation'
    | 'minimumRegistrations'
    | 'minimumRegistrationsBy'

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
 * @param event the event being announced
 * @param date  the occurrence being announced, which a deadline counted in days is measured from
 * @param say   writes one sentence
 */
export function announcementSentences(event: StationEvent, date: string | null, say: Say): string[] {
    if (event.cancelled) {
        return [event.cancelReason?.trim() ? say('cancelledBecause', {reason: event.cancelReason.trim()}) : say('cancelled')]
    }
    if (!event.requiresRegistration) return []
    return [
        closing(event, date, say),
        event.registrationLimit ? say('registrationLimit', {count: event.registrationLimit}) : '',
        event.requiresConfirmation ? say('registrationConfirmation') : '',
        minimum(event, say),
    ].filter(sentence => sentence !== '')
}

function closing(event: StationEvent, date: string | null, say: Say): string {
    if (event.registrationDeadline) {
        return say('registrationClosesOn', {date: formatDateTime(event.registrationDeadline)})
    }
    const days = event.registrationCloseDays
    if (!days) return say('registrationRequired')
    if (!date) return say('registrationClosesDaysBefore', {days})
    return say('registrationClosesOn', {date: formatWeekdayDate(daysBefore(date, days))})
}

function minimum(event: StationEvent, say: Say): string {
    if (!event.minRegistrations) return ''
    return event.thresholdDate
        ? say('minimumRegistrationsBy', {count: event.minRegistrations, date: formatDate(event.thresholdDate)})
        : say('minimumRegistrations', {count: event.minRegistrations})
}

/** The calendar day a number of days before another, both as `YYYY-MM-DD`. */
function daysBefore(date: string, days: number): string {
    const day = new Date(`${date}T00:00:00Z`)
    day.setUTCDate(day.getUTCDate() - days)
    return day.toISOString().slice(0, 10)
}
