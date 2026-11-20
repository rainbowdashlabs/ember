/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {LentOutItem} from '@/api/generated/schema'
import {piecesHeldByLoans} from './loanHolds'

function loan(over: Partial<LentOutItem>): LentOutItem {
    return {
        requestId: 1,
        requestItemId: 1,
        itemId: null,
        quantity: 1,
        assignedItemId: 7,
        status: 'APPROVED',
        dateFrom: '2026-11-01',
        dateTo: '2026-11-30',
        requestingStationName: 'FF Partner',
        ...over,
    } as LentOutItem
}

describe('piecesHeldByLoans', () => {
    it('holds a piece from approval until the end date has passed', () => {
        const held = piecesHeldByLoans([
            loan({assignedItemId: 7}),
            loan({assignedItemId: 8, status: 'LENT', dateTo: null}),
            loan({assignedItemId: 9, dateTo: '2026-11-19'}),
            loan({assignedItemId: 10, status: 'REQUESTED'}),
            loan({assignedItemId: 11, dateTo: '2026-11-20'}),
        ], '2026-11-20')

        expect(held.get(7)).toBe('FF Partner')
        expect(held.has(8), 'a loan without an end').toBe(true)
        expect(held.has(9), 'a loan that is over').toBe(false)
        expect(held.has(10), 'a loan only asked for').toBe(false)
        expect(held.has(11), 'the last day still holds').toBe(true)
    })
})
