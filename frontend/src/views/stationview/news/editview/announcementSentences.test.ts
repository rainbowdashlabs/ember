/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {announcementSentences, type AnnouncementSentence} from './announcementSentences'
import type {RegistrationDeadline, StationEvent} from '@/api/generated/schema'
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

describe('announcementSentences', () => {
    it('says nothing about signing up for an event without registration', () => {
        expect(announcementSentences({...EVENT, requiresRegistration: false, registrationLimit: 5}, null, say)).toEqual([])
    })

    it('names each registration setting that is set, on the station\'s clock', () => {
        const sentences = announcementSentences(
            {
                ...EVENT,
                registrationDeadline: {kind: 'FIXED', at: '2026-07-01T08:00:00Z', offsetMinutes: null, anchor: null},
                registrationLimit: 30,
                requiresConfirmation: true,
                minRegistrations: 8,
                thresholdDays: 5,
            },
            '2026-07-20',
            say,
            'Europe/Berlin',
        )

        expect(sentences).toEqual([
            'registrationClosesOn {"date":"01.07.2026, 10:00"}',
            'registrationLimit {"count":30}',
            'registrationConfirmation',
            'minimumRegistrationsBy {"count":8,"date":"Mittwoch, 15.07.2026"}',
        ])
    })

    it('keeps the minimum in days where there is no occurrence to count from', () => {
        expect(announcementSentences({...EVENT, minRegistrations: 8, thresholdDays: 5}, null, say))
            .toContain('minimumRegistrationsDaysBefore {"count":8,"days":5}')
    })

    it('names the deadline of the announced date of a series', () => {
        const event = {...EVENT, ...WEEKLY, requiresConfirmation: true, registrationDeadline: THREE_DAYS_AT_MIDNIGHT}
        expect(announcementSentences(event, '2026-07-31', say, 'Europe/Berlin'))
            .toEqual([
                'registrationClosesOn {"date":"28.07.2026, 00:00"}',
                'registrationConfirmation',
            ])
    })

    it('names the deadline of a one-off appointment announced without a date from its own start', () => {
        expect(announcementSentences({...EVENT, registrationDeadline: THREE_DAYS_AT_MIDNIGHT}, null, say, 'Europe/Berlin'))
            .toEqual(['registrationClosesOn {"date":"21.07.2026, 00:00"}'])
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
