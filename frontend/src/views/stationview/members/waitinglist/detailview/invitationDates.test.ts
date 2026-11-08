/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {emptyDate, hasDuplicateDates, pickedDates, type InvitationDateDraft} from './invitationDates'

function picked(eventId: number, date: string, arrivalTime = ''): InvitationDateDraft {
    return {...emptyDate(), occurrence: {eventId, date} as InvitationDateDraft['occurrence'], arrivalTime}
}

describe('invitation dates', () => {
    it('gives every new row an id of its own', () => {
        expect(emptyDate().id).not.toBe(emptyDate().id)
    })

    it('sends the rows with a date picked, in their order, without an empty arrival time', () => {
        expect(pickedDates([picked(3, '2026-05-19', '17:30'), emptyDate(), picked(4, '2026-05-12')])).toEqual([
            {eventId: 3, date: '2026-05-19', arrivalTime: '17:30'},
            {eventId: 4, date: '2026-05-12', arrivalTime: null},
        ])
    })

    it('finds the same date of the same appointment picked twice', () => {
        expect(hasDuplicateDates([picked(3, '2026-05-19'), picked(3, '2026-05-19', '17:00')])).toBe(true)
    })

    it('lets two dates of one appointment and one date of two appointments through', () => {
        expect(hasDuplicateDates([picked(3, '2026-05-19'), picked(3, '2026-05-26'), picked(4, '2026-05-19')]))
            .toBe(false)
        expect(hasDuplicateDates([emptyDate(), emptyDate()])).toBe(false)
    })
})
