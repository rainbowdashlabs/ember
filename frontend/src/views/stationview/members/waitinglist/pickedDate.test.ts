/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {WaitingListAnswer, type WaitingListEntryWithScore} from '@/api/generated/schema'
import {pickedOption} from './pickedDate'

type Entry = WaitingListEntryWithScore['entry']

const OPTIONS = [
    {position: 0, eventId: 3, date: '2026-06-02', arrivalTime: '17:45:00', appointmentName: 'Übungsabend'},
    {position: 1, eventId: 3, date: '2026-06-09', arrivalTime: null, appointmentName: 'Übungsabend'},
]

function entry(answer: Entry['answer']): Entry {
    return {invitation: {message: '', options: OPTIONS}, answer} as unknown as Entry
}

/** The date an answer picked is read off the dates the invitation offers, and is none without a coming answer. */
describe('pickedOption', () => {
    it('finds the offered date a coming answer picked', () => {
        const picked = pickedOption(entry({answer: WaitingListAnswer.COMING, answeredAt: '', note: '', position: 1}))

        expect(picked?.date).toBe('2026-06-09')
    })

    it('finds none without an answer or for an answer that picked no date', () => {
        expect(pickedOption(entry(null))).toBeNull()
        expect(pickedOption(entry({answer: WaitingListAnswer.DATE_DOES_NOT_SUIT, answeredAt: '', note: '', position: null})))
            .toBeNull()
    })
})
