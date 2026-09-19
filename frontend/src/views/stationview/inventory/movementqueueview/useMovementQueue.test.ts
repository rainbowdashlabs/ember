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

const ALL = [OURS, OURS_OLDER, THEIRS, THE_BODYS, FINISHED]

function idsOf(queue: ReturnType<typeof useMovementQueue>): number[] {
    return queue.matching.value.map(row => row.id)
}

describe('useMovementQueue', () => {
    it('leaves finished movements out until somebody asks for them', () => {
        const queue = useMovementQueue(() => ALL)

        expect(idsOf(queue)).toEqual([1, 2, 3, 4])

        queue.states.value = ['DONE']

        expect(idsOf(queue)).toEqual([5])
    })

    it('narrows by whose turn it is', () => {
        const queue = useMovementQueue(() => ALL)

        queue.turns.value = ['MEMBER']

        expect(idsOf(queue)).toEqual([3])
    })

    it('narrows by purpose and by inventory', () => {
        const queue = useMovementQueue(() => ALL)

        queue.purposes.value = ['RETURN']
        expect(idsOf(queue)).toEqual([3])

        queue.purposes.value = []
        queue.inventoryIds.value = ['3']
        expect(idsOf(queue)).toEqual([1, 2])
    })

    it('searches the member, the piece and what is written on it', () => {
        const queue = useMovementQueue(() => ALL)

        queue.search.value = 'müller'
        expect(idsOf(queue)).toEqual([3])

        queue.search.value = 'helm 7'
        expect(idsOf(queue)).toEqual([1])
    })

    it('offers only the inventories the rows actually mention', () => {
        const queue = useMovementQueue(() => ALL)

        expect(queue.inventories.value.map(choice => choice.name)).toEqual(['Helme', 'Jacken'])
    })
})
