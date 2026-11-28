/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {BorrowedItemResponse, LendingRequestResponse, LendingStatus} from '@/api/generated/schema'
import {borrowBlocks, borrowedGroups, lendBlocks} from './overviewBlocks'

const TODAY = '2030-06-10'

function entry(id: number, isOwner: boolean, status: LendingStatus, from: string, to: string | null, overdue = false, created = '2030-06-01T00:00:00Z'): LendingRequestResponse {
    return {
        allowedSteps: [] as LendingStatus[], isOwner, overdue, itemSummary: '', owningStationName: 'Nord', requestingStationName: 'Süd',
        request: {
            id, status, requestedDateFrom: from, requestedDateTo: to ?? from, createdAt: created, createdBy: null,
            eventDate: null, eventId: null, occasion: '', owningStationUid: 'a', requestingStationUid: 'b', uid: String(id),
        },
    } as LendingRequestResponse
}

/** The overview sorts every request into the block that asks for the next step. */
describe('overviewBlocks', () => {
    it('puts the station\'s own gear into answer, hand out, out and overdue', () => {
        const blocks = lendBlocks([
            entry(1, true, 'REQUESTED', '2030-07-01', null, false, '2030-06-05T00:00:00Z'),
            entry(2, true, 'REQUESTED', '2030-07-01', null, false, '2030-06-02T00:00:00Z'),
            entry(3, true, 'APPROVED', '2030-06-15', '2030-06-16'),
            entry(8, true, 'APPROVED', '2030-06-17', '2030-06-18'),
            entry(4, true, 'APPROVED', '2030-06-30', '2030-07-01'),
            entry(5, true, 'LENT', '2030-06-01', '2030-06-20'),
            entry(6, true, 'LENT', '2030-05-01', '2030-05-05', true),
            entry(7, false, 'REQUESTED', '2030-07-01', null),
        ], TODAY)

        expect(blocks.toAnswer.map(e => e.request.id)).toEqual([2, 1])
        expect(blocks.handOutSoon.map(e => e.request.id)).toEqual([3, 8])
        expect(blocks.outNow.map(e => e.request.id)).toEqual([5])
        expect(blocks.overdue.map(e => e.request.id)).toEqual([6])
    })

    it('puts the station\'s requests into waiting, pick up and give back', () => {
        const blocks = borrowBlocks([
            entry(1, false, 'REQUESTED', '2030-07-01', null),
            entry(2, false, 'APPROVED', '2030-06-12', '2030-06-14'),
            entry(3, false, 'LENT', '2030-06-01', '2030-06-12'),
            entry(4, false, 'LENT', '2030-06-01', '2030-08-01'),
            entry(5, false, 'LENT', '2030-05-01', '2030-05-03', true),
            entry(6, true, 'REQUESTED', '2030-07-01', null),
        ], TODAY)

        expect(blocks.waiting.map(e => e.request.id)).toEqual([1])
        expect(blocks.pickUpSoon.map(e => e.request.id)).toEqual([2])
        expect(blocks.giveBackSoon.map(e => e.request.id)).toEqual([5, 3])
    })

    it('groups borrowed pieces by request, the one due first leading', () => {
        const item = (name: string) => ({name, internalId: name.slice(0, 1)}) as BorrowedItemResponse['item']
        const groups = borrowedGroups([
            {loanRequestId: 8, ownerStationName: 'Nord', dueOn: '2030-06-20', item: item('Zelt')},
            {loanRequestId: 9, ownerStationName: 'Süd', dueOn: '2030-06-12', item: item('Funk 1')},
            {loanRequestId: 9, ownerStationName: 'Süd', dueOn: '2030-06-12', item: item('Funk 2')},
        ])

        expect(groups).toEqual([
            {requestId: 9, stationName: 'Süd', dueOn: '2030-06-12', pieces: [{name: 'Funk 1', internalId: 'F'}, {name: 'Funk 2', internalId: 'F'}]},
            {requestId: 8, stationName: 'Nord', dueOn: '2030-06-20', pieces: [{name: 'Zelt', internalId: 'Z'}]},
        ])
    })
})
