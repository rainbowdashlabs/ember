/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {partnerDateState} from './partnerDatePlaces'
import type {PartnerEventDetail, RemoteDatePlaces} from '@/api/generated/schema'

function detail(datePlaces: RemoteDatePlaces | null, requiresConfirmation = false, decidesItself = false): PartnerEventDetail {
    return {
        event: {requiresConfirmation} as PartnerEventDetail['event'],
        publicFields: [],
        questions: [],
        places: decidesItself ? {slotBudget: null, decidesItself: true} : null,
        datePlaces,
    }
}

/** What the places a partner counted show about signing up for the date a sign-up is filed under. */
describe('partnerDateState', () => {
    it('reads a date whose places are all taken as full, where a sign-up waits', () => {
        expect(partnerDateState(detail({date: '2026-12-01', limit: 2, taken: 2, waiting: 0}), '2026-12-01'))
            .toEqual({full: true, joinsQueue: true})
    })

    it('sends a sign-up to the waiting list where others already wait, though a place is free', () => {
        expect(partnerDateState(detail({date: '2026-12-01', limit: 3, taken: 2, waiting: 1}), '2026-12-01'))
            .toEqual({full: false, joinsQueue: true})
    })

    it('holds a sign-up for a decision rather than the waiting list where somebody decides first', () => {
        const full = {date: '2026-12-01', limit: 1, taken: 1, waiting: 0}
        expect(partnerDateState(detail(full, true), '2026-12-01')).toEqual({full: true, joinsQueue: false})
        expect(partnerDateState(detail(full, false, true), '2026-12-01')).toEqual({full: true, joinsQueue: false})
    })

    it('says nothing about another date, a date without a limit or a partner that counted nothing', () => {
        expect(partnerDateState(detail({date: '2026-12-08', limit: 1, taken: 1, waiting: 0}), '2026-12-01'))
            .toEqual({full: false, joinsQueue: false})
        expect(partnerDateState(detail({date: '2026-12-01', limit: null, taken: 9, waiting: 0}), '2026-12-01'))
            .toEqual({full: false, joinsQueue: false})
        expect(partnerDateState(detail(null), '2026-12-01')).toEqual({full: false, joinsQueue: false})
        expect(partnerDateState(null, '2026-12-01')).toEqual({full: false, joinsQueue: false})
    })
})
