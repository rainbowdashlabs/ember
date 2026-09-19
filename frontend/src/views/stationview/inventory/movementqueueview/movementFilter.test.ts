/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {hasMoved, lastMovedAt, queueOrder} from './movementFilter'
import type {Movement} from '@/api/movements'

function movement(over: Partial<Movement>): Movement {
    return {
        id: 1,
        purpose: 'EXCHANGE',
        state: 'OPEN',
        reason: '',
        createdAt: '2026-09-01T10:00:00Z',
        ...over,
    } as Movement
}

describe('hasMoved', () => {
    it('says a movement nobody has touched has not moved', () => {
        expect(hasMoved(movement({createdAt: '2026-09-01T10:00:00Z'}))).toBe(false)
        expect(hasMoved(movement({
            createdAt: '2026-09-01T10:00:00Z',
            updatedAt: '2026-09-01T10:00:00Z',
        }))).toBe(false)
    })

    /**
     * The reported case. Most movements are dealt with the day they are raised, and judging this
     * on the printed day rather than the timestamp hid the date on every one of them.
     */
    it('says a movement touched later the same day has moved', () => {
        expect(hasMoved(movement({
            createdAt: '2026-09-01T09:00:00Z',
            updatedAt: '2026-09-01T16:30:00Z',
        }))).toBe(true)
    })

    it('says a movement touched on a later day has moved', () => {
        expect(hasMoved(movement({
            createdAt: '2026-09-01T10:00:00Z',
            updatedAt: '2026-09-20T10:00:00Z',
        }))).toBe(true)
    })
})

describe('lastMovedAt', () => {
    it('is when it last moved', () => {
        expect(lastMovedAt(movement({updatedAt: '2026-09-20T10:00:00Z'}))).toBe('2026-09-20T10:00:00Z')
    })

    it('falls back to the day it was raised when it never moved', () => {
        expect(lastMovedAt(movement({createdAt: '2026-09-01T10:00:00Z'}))).toBe('2026-09-01T10:00:00Z')
    })
})

describe('queueOrder', () => {
    it('puts the rows we can act on first, the oldest of them first, and the finished ones last', () => {
        const rows = [
            movement({id: 4, currentStepActor: 'OWNER'}),
            movement({id: 5, state: 'DONE', currentStepActor: null}),
            movement({id: 3, currentStepActor: 'MEMBER'}),
            movement({id: 1, currentStepActor: 'STATION', actionable: true, createdAt: '2026-09-05T10:00:00Z'}),
            movement({id: 2, currentStepActor: 'STATION', actionable: true, createdAt: '2026-08-01T10:00:00Z'}),
        ]

        expect(rows.toSorted(queueOrder).map(row => row.id)).toEqual([2, 1, 3, 4, 5])
    })
})
