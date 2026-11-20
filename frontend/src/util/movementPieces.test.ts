/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {MovementResponse} from '@/api/generated/schema'
import {arrivingForMember, fillsAGap, piecesOnOpenMovements} from './movementPieces'

function movement(over: Partial<MovementResponse>): MovementResponse {
    return {
        id: 1,
        state: 'OPEN',
        purpose: 'ISSUE',
        memberId: 41,
        itemId: null,
        incomingItemId: null,
        ...over,
    } as MovementResponse
}

describe('piecesOnOpenMovements', () => {
    it('names both ends of an open movement and nothing of a closed one', () => {
        const pieces = piecesOnOpenMovements([
            movement({id: 78, incomingItemId: 57}),
            movement({id: 79, purpose: 'EXCHANGE', itemId: 10, incomingItemId: 11}),
            movement({id: 80, state: 'DONE', incomingItemId: 12}),
        ])

        expect(pieces.get(57), 'the promised piece').toBe(78)
        expect(pieces.get(10), 'the piece setting out').toBe(79)
        expect(pieces.get(11)).toBe(79)
        expect(pieces.has(12), 'a finished movement carries nothing').toBe(false)
    })
})

describe('arrivingForMember', () => {
    it('keeps what is still on its way to the member', () => {
        const arriving = arrivingForMember([
            movement({id: 78, incomingItemId: 57}),
            movement({id: 79, incomingItemId: null}),
            movement({id: 80, incomingItemId: 58}),
            movement({id: 81, purpose: 'RETURN', itemId: 59}),
            movement({id: 82, memberId: 7, incomingItemId: 60}),
            movement({id: 83, state: 'CANCELLED', incomingItemId: 61}),
        ], 41, new Set([58]))

        expect(arriving.map(m => m.id)).toEqual([78, 79])
    })
})

describe('fillsAGap', () => {
    it('counts a hand-out against a gap', () => {
        expect(fillsAGap(movement({incomingItemId: 57}), new Set())).toBe(true)
    })

    it('counts an exchange only once the swapped piece has left the member', () => {
        const swap = movement({purpose: 'EXCHANGE', itemId: 10, incomingItemId: 11})

        expect(fillsAGap(swap, new Set([10])), 'the member still holds the old piece').toBe(false)
        expect(fillsAGap(swap, new Set()), 'the old piece has been handed in').toBe(true)
    })
})
