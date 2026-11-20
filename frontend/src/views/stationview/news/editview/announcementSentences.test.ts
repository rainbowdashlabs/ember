/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {announcementSentences, deadlineDay, type AnnouncementSentence} from './announcementSentences'
import type {DateDeadlines, RegistrationDeadline, StationEvent} from '@/api/generated/schema'
import {noDeadline} from '@/views/stationview/events/eventshared/registrationDeadline'

const THREE_DAYS_AT_MIDNIGHT: RegistrationDeadline = {kind: 'RELATIVE', at: null, offsetMinutes: 3 * 1440, anchor: 'MIDNIGHT'}
const WEEKLY: Partial<StationEvent> = {eventType: 'RECURRING', recurring: true}

const say = (sentence: AnnouncementSentence, values?: Record<string, string | number>) =>
    values ? `${sentence} ${JSON.stringify(values)}` : sentence

const EVENT: StationEvent = {
    id: 7, stationId: 'abc', name: 'Zeltlager', description: null, eventType: 'ONE_TIME', recurring: false,
    startTime: '2026-07-24T08:00:00Z', endTime: '2026-07-26T16:00:00Z', dayOfWeek: null, templateId: null,
    categoryId: null, isPublic: null, restricted: false, restrictionMode: 'AND', viewRestrictionMode: 'AND',
    requiresRegistration: true, requiresConfirmation: false, registrationDeadline: noDeadline(), registrationLimit: null, queueOrder: null,
    minRegistrations: null, thresholdDays: null, repeatUntil: null, repeatCount: null,
    cancelled: false, cancelledAt: null, cancelReason: null,
}

/** What the server answers for a date: when sign-up closes and when the minimum is checked. */
function deadlines(registrationClosesAt: string | null, minimumCheckedAt: string | null = null, date = '2026-07-20'): DateDeadlines {
    return {date, registrationClosesAt, minimumCheckedAt}
}

describe('announcementSentences', () => {
    it('says nothing about signing up for an event without registration', () => {
        expect(announcementSentences({...EVENT, requiresRegistration: false, registrationLimit: 5}, null, say)).toEqual([])
    })

    it('names each registration setting that is set, the moments on the station\'s clock', () => {
        const sentences = announcementSentences(
            {
                ...EVENT,
                registrationDeadline: {kind: 'FIXED', at: '2026-07-01T08:00:00Z', offsetMinutes: null, anchor: null},
                registrationLimit: 30,
                requiresConfirmation: true,
                minRegistrations: 8,
                thresholdDays: 5,
            },
            deadlines('2026-07-01T08:00:00Z', '2026-07-14T22:00:00Z'),
            say,
            'Europe/Berlin',
        )

        expect(sentences).toEqual([
            'registrationClosesOn {"date":"01.07.2026, 10:00"}',
            'registrationLimit {"count":30}',
            'registrationConfirmation',
            'minimumRegistrationsBy {"count":8,"date":"Dienstag, 14.07.2026"}',
        ])
    })

    /**
     * The minimum is checked at midnight at the start of the day the server names, so the last day to
     * sign up on is the station's day before it, not that day itself.
     */
    it('names the day before the minimum check as the last day to reach it', () => {
        const sentences = announcementSentences(
            {...EVENT, minRegistrations: 8, thresholdDays: 5},
            deadlines(null, '2026-07-14T22:00:00Z'),
            say,
            'Europe/Berlin',
        )

        expect(sentences).toContain('minimumRegistrationsBy {"count":8,"date":"Dienstag, 14.07.2026"}')
        expect(sentences.join(' ')).not.toContain('15.07.2026')
    })

    it('names a fixed deadline by its own moment where the server gave none', () => {
        const fixed: RegistrationDeadline = {kind: 'FIXED', at: '2026-07-01T08:00:00Z', offsetMinutes: null, anchor: null}
        expect(announcementSentences({...EVENT, registrationDeadline: fixed}, null, say, 'Europe/Berlin'))
            .toEqual(['registrationClosesOn {"date":"01.07.2026, 10:00"}'])
    })

    it('keeps the minimum in days where there is no date to count from', () => {
        expect(announcementSentences({...EVENT, minRegistrations: 8, thresholdDays: 5}, null, say))
            .toContain('minimumRegistrationsDaysBefore {"count":8,"days":5}')
    })

    it('names the closing moment the server sends for the date', () => {
        const event = {...EVENT, ...WEEKLY, requiresConfirmation: true, registrationDeadline: THREE_DAYS_AT_MIDNIGHT}
        expect(announcementSentences(event, deadlines('2026-07-27T22:00:00Z', null, '2026-07-31'), say, 'Europe/Berlin'))
            .toEqual([
                'registrationClosesOn {"date":"28.07.2026, 00:00"}',
                'registrationConfirmation',
            ])
    })

    it('says the rule in words for a series announced without a date', () => {
        expect(announcementSentences({...EVENT, ...WEEKLY, registrationDeadline: THREE_DAYS_AT_MIDNIGHT}, null, say))
            .toEqual(['registrationClosesBeforeAtMidnight {"span":"spanDays {\\"count\\":3}"}'])
        const twoHours: RegistrationDeadline = {kind: 'RELATIVE', at: null, offsetMinutes: 120, anchor: 'EXACT'}
        expect(announcementSentences({...EVENT, ...WEEKLY, registrationDeadline: twoHours}, null, say))
            .toEqual(['registrationClosesBefore {"span":"spanHours {\\"count\\":2}"}'])
    })

    it('says a cancelled event is off, with its reason, and nothing about signing up', () => {
        expect(announcementSentences({...EVENT, cancelled: true, cancelReason: ' Unwetter ', registrationLimit: 5}, null, say))
            .toEqual(['cancelledBecause {"reason":"Unwetter"}'])
        expect(announcementSentences({...EVENT, cancelled: true}, null, say)).toEqual(['cancelled'])
    })
})

/** The date the deadlines are asked for: the announced one, or the first day where every date closes alike. */
describe('deadlineDay', () => {
    it('takes the announced date', () => {
        expect(deadlineDay({...EVENT, ...WEEKLY}, '2026-07-31', 'Europe/Berlin')).toBe('2026-07-31')
    })

    it('takes the first day of a one-off appointment or of a series with a fixed deadline', () => {
        expect(deadlineDay(EVENT, null, 'Europe/Berlin')).toBe('2026-07-24')
        const fixed: RegistrationDeadline = {kind: 'FIXED', at: '2026-07-01T08:00:00Z', offsetMinutes: null, anchor: null}
        expect(deadlineDay({...EVENT, ...WEEKLY, registrationDeadline: fixed}, null, 'Europe/Berlin')).toBe('2026-07-24')
    })

    it('has no day for a series with a relative deadline announced without a date', () => {
        expect(deadlineDay({...EVENT, ...WEEKLY, registrationDeadline: THREE_DAYS_AT_MIDNIGHT}, null, 'Europe/Berlin')).toBeNull()
    })
})
