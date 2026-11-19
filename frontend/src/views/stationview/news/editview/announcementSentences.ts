/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {StationEvent} from '@/api/generated/schema'
import {formatDateTime, formatWeekdayDate, shiftIsoDate, stationDayOf} from '@/util/format'
import {closesOnDate} from '@/views/stationview/events/eventshared/dateClosing'
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
 * <p>The deadline is named as the moment it falls on for the announced date. Only a series announced
 * without a date, whose relative deadline falls differently for every date, has it said as the rule
 * itself. The minimum of registrations is counted back from the occurrence, since it has to be reached
 * a number of days before each date.
 *
 * @param event    the event being announced
 * @param date     the occurrence being announced, which the deadline and the minimum are counted from
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
        registration(event, date, say, timezone),
        event.registrationLimit ? say('registrationLimit', {count: event.registrationLimit}) : '',
        event.requiresConfirmation ? say('registrationConfirmation') : '',
        minimum(event, date, say),
    ].filter(sentence => sentence !== '')
}

/**
 * That signing up is needed, and until when: the moment for the announced date, or for a one-off
 * appointment its own date, and the rule in words for a series announced without a date.
 */
function registration(event: StationEvent, date: string | null, say: Say, timezone?: string | null): string {
    const rule = event.registrationDeadline
    if (!rule || rule.kind === 'NONE') return say('registrationRequired')
    const day = date ?? (event.eventType === 'ONE_TIME' ? stationDayOf(new Date(event.startTime), timezone) : null)
    const closes = rule.kind === 'FIXED' ? rule.at : day && closesOnDate(rule, event.startTime, day, timezone)
    if (closes) return say('registrationClosesOn', {date: formatDateTime(closes, timezone)})
    if (rule.kind !== 'RELATIVE' || day) return say('registrationRequired')
    const span = spanOf(rule.offsetMinutes ?? 0, say)
    return rule.anchor === 'MIDNIGHT'
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

function minimum(event: StationEvent, date: string | null, say: Say): string {
    const count = event.minRegistrations
    if (!count) return ''
    const days = event.thresholdDays
    if (days == null) return say('minimumRegistrations', {count})
    if (!date) return say('minimumRegistrationsDaysBefore', {count, days})
    return say('minimumRegistrationsBy', {count, date: formatWeekdayDate(shiftIsoDate(date, -days))})
}
