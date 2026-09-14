/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {useMovementQueue} from './useMovementQueue'
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

const OURS = movement({
    id: 1,
    currentStepActor: 'STATION',
    actionable: true,
    memberName: 'Zoe Abel',
    inventoryId: 3,
    inventoryName: 'Helme',
    itemName: 'Helm 7',
    createdAt: '2026-09-05T10:00:00Z',
})
const OURS_OLDER = movement({
    id: 2,
    currentStepActor: 'STATION',
    actionable: true,
    memberName: 'Anna Zimmer',
    inventoryId: 3,
    inventoryName: 'Helme',
    createdAt: '2026-08-01T10:00:00Z',
})
const THEIRS = movement({
    id: 3,
    currentStepActor: 'MEMBER',
    actionable: false,
    memberName: 'Ben Müller',
    inventoryId: 4,
    inventoryName: 'Jacken',
    purpose: 'RETURN',
})
const THE_BODYS = movement({id: 4, currentStepActor: 'OWNER', actionable: false, purpose: 'REQUEST'})
const FINISHED = movement({id: 5, state: 'DONE', currentStepActor: null, inventoryName: 'Helme', inventoryId: 3})

const ALL = [THE_BODYS, THEIRS, OURS, FINISHED, OURS_OLDER]

describe('useMovementQueue', () => {
    it('puts the rows we can act on first and the others behind them', () => {
        const queue = useMovementQueue(() => ALL)

        expect(queue.visible.value.map(row => row.id)).toEqual([2, 1, 3, 4])
    })

    it('leaves finished movements out until somebody asks for them', () => {
        const queue = useMovementQueue(() => ALL)

        expect(queue.visible.value.some(row => row.id === 5)).toBe(false)

        queue.states.value = ['DONE']

        expect(queue.visible.value.map(row => row.id)).toEqual([5])
    })

    it('narrows by whose turn it is', () => {
        const queue = useMovementQueue(() => ALL)

        queue.turns.value = ['MEMBER']

        expect(queue.visible.value.map(row => row.id)).toEqual([3])
    })

    it('narrows by purpose and by inventory', () => {
        const queue = useMovementQueue(() => ALL)

        queue.purposes.value = ['RETURN']
        expect(queue.visible.value.map(row => row.id)).toEqual([3])

        queue.purposes.value = []
        queue.inventoryIds.value = ['3']
        expect(queue.visible.value.map(row => row.id)).toEqual([2, 1])
    })

    it('searches the member, the piece and what is written on it', () => {
        const queue = useMovementQueue(() => ALL)

        queue.search.value = 'müller'
        expect(queue.visible.value.map(row => row.id)).toEqual([3])

        queue.search.value = 'helm 7'
        expect(queue.visible.value.map(row => row.id)).toEqual([1])
    })

    it('offers only the inventories the rows actually mention', () => {
        const queue = useMovementQueue(() => ALL)

        expect(queue.inventories.value.map(choice => choice.name)).toEqual(['Helme', 'Jacken'])
    })

    it('hands the order to a column once one is picked', () => {
        const queue = useMovementQueue(() => ALL)

        queue.selectSort('member')

        expect(queue.visible.value.map(row => row.memberName ?? '')).toEqual(['', 'Anna Zimmer', 'Ben Müller', 'Zoe Abel'])
    })

    it('orders by the day a movement was raised, newest first', () => {
        const queue = useMovementQueue(() => ALL)

        queue.selectSort('created')

        expect(queue.visible.value.map(row => row.createdAt)).toEqual([
            '2026-09-05T10:00:00Z',
            '2026-09-01T10:00:00Z',
            '2026-09-01T10:00:00Z',
            '2026-08-01T10:00:00Z',
        ])
    })

    /**
     * The row raised longest ago but touched most recently has to come first here and last under
     * the other date, which is the whole reason the two orders are told apart.
     */
    it('orders by the day a movement last moved, newest first', () => {
        const touched = [
            movement({id: 6, createdAt: '2026-08-01T10:00:00Z', updatedAt: '2026-09-20T10:00:00Z'}),
            movement({id: 7, createdAt: '2026-09-10T10:00:00Z', updatedAt: '2026-09-11T10:00:00Z'}),
        ]
        const queue = useMovementQueue(() => touched)

        queue.selectSort('modified')
        expect(queue.visible.value.map(row => row.id)).toEqual([6, 7])

        queue.selectSort('created')
        expect(queue.visible.value.map(row => row.id)).toEqual([7, 6])
    })

    it('falls back to the day it was raised for a movement that never moved', () => {
        const never = [
            movement({id: 8, createdAt: '2026-09-15T10:00:00Z'}),
            movement({id: 9, createdAt: '2026-09-02T10:00:00Z', updatedAt: '2026-09-14T10:00:00Z'}),
        ]
        const queue = useMovementQueue(() => never)

        queue.selectSort('modified')

        expect(queue.visible.value.map(row => row.id)).toEqual([8, 9])
    })
})
