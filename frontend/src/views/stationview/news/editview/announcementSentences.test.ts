/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {announcementSentences, type AnnouncementSentence} from './announcementSentences'
import type {StationEvent} from '@/api/generated/schema'

const say = (sentence: AnnouncementSentence, values?: Record<string, string | number>) =>
    values ? `${sentence} ${JSON.stringify(values)}` : sentence

const EVENT: StationEvent = {
    id: 7, stationId: 'abc', name: 'Zeltlager', description: null, eventType: 'ONE_TIME', recurring: false,
    startTime: '2026-07-24T08:00:00Z', endTime: '2026-07-26T16:00:00Z', dayOfWeek: null, templateId: null,
    categoryId: null, isPublic: null, restricted: false, restrictionMode: 'AND', viewRestrictionMode: 'AND',
    requiresRegistration: true, requiresConfirmation: false, registrationDeadline: null, registrationLimit: null,
    registrationCloseDays: null, minRegistrations: null, thresholdDays: null, repeatUntil: null, repeatCount: null,
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
                registrationDeadline: '2026-07-01T08:00:00Z',
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

    it('says when unconfirmed sign-ups lapse, counted back from the occurrence', () => {
        expect(announcementSentences({...EVENT, requiresConfirmation: true, registrationCloseDays: 3}, '2026-07-20', say))
            .toEqual([
                'registrationRequired',
                'registrationConfirmation',
                'unconfirmedLapseOn {"date":"Freitag, 17.07.2026"}',
            ])
    })

    it('keeps the lapse in days where there is no occurrence to count from', () => {
        expect(announcementSentences({...EVENT, requiresConfirmation: true, registrationCloseDays: 3}, null, say))
            .toContain('unconfirmedLapseDaysBefore {"days":3}')
    })

    it('leaves the days-before setting out where nothing waits for confirmation', () => {
        expect(announcementSentences({...EVENT, registrationCloseDays: 3, minRegistrations: 4}, '2026-07-20', say))
            .toEqual(['registrationRequired', 'minimumRegistrations {"count":4}'])
    })

    it('says a cancelled event is off, with its reason, and nothing about signing up', () => {
        expect(announcementSentences({...EVENT, cancelled: true, cancelReason: ' Unwetter ', registrationLimit: 5}, null, say))
            .toEqual(['cancelledBecause {"reason":"Unwetter"}'])
        expect(announcementSentences({...EVENT, cancelled: true}, null, say)).toEqual(['cancelled'])
    })
})
