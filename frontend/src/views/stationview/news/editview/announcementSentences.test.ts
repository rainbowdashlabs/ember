/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {announcementSentences, type AnnouncementSentence} from './announcementSentences'
import type {StationEvent} from '@/api/events'

const say = (sentence: AnnouncementSentence, values?: Record<string, string | number>) =>
    values ? `${sentence} ${JSON.stringify(values)}` : sentence

const EVENT: StationEvent = {id: 7, stationId: 'abc', name: 'Zeltlager', requiresRegistration: true}

describe('announcementSentences', () => {
    it('says nothing about signing up for an event without registration', () => {
        expect(announcementSentences({...EVENT, requiresRegistration: false, registrationLimit: 5}, null, say)).toEqual([])
    })

    it('names each registration setting that is set', () => {
        const sentences = announcementSentences(
            {
                ...EVENT,
                registrationDeadline: '2026-07-01T10:00:00',
                registrationLimit: 30,
                requiresConfirmation: true,
                minRegistrations: 8,
                thresholdDate: '2026-06-20T00:00:00',
            },
            '2026-07-20',
            say,
        )

        expect(sentences).toEqual([
            'registrationClosesOn {"date":"01.07.2026, 10:00"}',
            'registrationLimit {"count":30}',
            'registrationConfirmation',
            'minimumRegistrationsBy {"count":8,"date":"20.06.2026"}',
        ])
    })

    it('counts a deadline in days back from the occurrence being announced', () => {
        expect(announcementSentences({...EVENT, registrationCloseDays: 3}, '2026-07-20', say))
            .toEqual(['registrationClosesOn {"date":"Freitag, 17.07.2026"}'])
    })

    it('keeps the deadline in days where there is no occurrence to count from', () => {
        expect(announcementSentences({...EVENT, registrationCloseDays: 3}, null, say))
            .toEqual(['registrationClosesDaysBefore {"days":3}'])
    })

    it('says only that registration is needed where no deadline is set', () => {
        expect(announcementSentences({...EVENT, minRegistrations: 4}, null, say))
            .toEqual(['registrationRequired', 'minimumRegistrations {"count":4}'])
    })

    it('says a cancelled event is off, with its reason, and nothing about signing up', () => {
        expect(announcementSentences({...EVENT, cancelled: true, cancelReason: ' Unwetter ', registrationLimit: 5}, null, say))
            .toEqual(['cancelledBecause {"reason":"Unwetter"}'])
        expect(announcementSentences({...EVENT, cancelled: true}, null, say)).toEqual(['cancelled'])
    })
})
