/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {
    defaultExchangeFilter,
    exchangeComparators,
    filterExchanges,
    inventoryChoices,
    openStatuses,
    statusChain,
    type ExchangeFilter,
} from './exchangeFilter'

interface RowSpec {
    id: number
    name: string
    inventoryId: number
    inventoryName: string
    status: ExchangeStatusName
    createdAt: string
}

function row(spec: RowSpec): ExchangeRequestEntry {
    return {
        id: spec.id,
        memberId: spec.id,
        memberName: spec.name,
        memberIdentity: {name: spec.name},
        inventoryId: spec.inventoryId,
        inventoryName: spec.inventoryName,
        inventoryType: 'INTERNAL',
        status: spec.status,
        reason: '',
        createdAt: spec.createdAt,
        updatedAt: spec.createdAt,
    }
}

const HELMETS = {inventoryId: 1, inventoryName: 'Helme'}
const JACKETS = {inventoryId: 2, inventoryName: 'Jacken'}
const BOOTS = {inventoryId: 3, inventoryName: 'Stiefel'}

const ANNA = row({id: 1, name: 'Anna Berger', ...HELMETS, status: ExchangeStatus.ANNOUNCED, createdAt: '2026-05-01T08:00:00Z'})
const BENNO = row({id: 2, name: 'Benno Klein', ...JACKETS, status: ExchangeStatus.DONE, createdAt: '2026-05-03T08:00:00Z'})
const CARLA = row({id: 3, name: 'Carla Anders', ...HELMETS, status: ExchangeStatus.SHIPPED, createdAt: '2026-05-02T08:00:00Z'})
const DORA = row({id: 4, name: 'Dora Anders', ...JACKETS, status: ExchangeStatus.ANNOUNCED, createdAt: '2026-05-04T08:00:00Z'})
const EMIL = row({id: 5, name: 'Emil Zoll', ...BOOTS, status: ExchangeStatus.RECEIVED, createdAt: '2026-05-05T08:00:00Z'})

const ALL = [ANNA, BENNO, CARLA, DORA, EMIL]

function ids(rows: ExchangeRequestEntry[]): number[] {
    return rows.map(r => r.id)
}

function filter(overrides: Partial<ExchangeFilter> = {}): ExchangeFilter {
    return {search: '', inventoryIds: [], statuses: [], ...overrides}
}

describe('filterExchanges', () => {
    it('keeps everything when nothing is asked for', () => {
        expect(ids(filterExchanges(ALL, filter()))).toEqual([1, 2, 3, 4, 5])
    })

    it('matches part of a member name without regard to case', () => {
        expect(ids(filterExchanges(ALL, filter({search: 'anders'})))).toEqual([3, 4])
        expect(ids(filterExchanges(ALL, filter({search: '  BERG  '})))).toEqual([1])
    })

    it('keeps only the exchanges of one inventory', () => {
        expect(ids(filterExchanges(ALL, filter({inventoryIds: ['2']})))).toEqual([2, 4])
    })

    it('keeps the exchanges of every inventory that was ticked', () => {
        expect(ids(filterExchanges(ALL, filter({inventoryIds: ['2', '3']})))).toEqual([2, 4, 5])
    })

    it('keeps only one status when one is ticked', () => {
        expect(ids(filterExchanges(ALL, filter({statuses: [ExchangeStatus.ANNOUNCED]})))).toEqual([1, 4])
    })

    it('keeps the exchanges of every status that was ticked', () => {
        const criteria = filter({statuses: [ExchangeStatus.ANNOUNCED, ExchangeStatus.SHIPPED, ExchangeStatus.DONE]})
        expect(ids(filterExchanges(ALL, criteria))).toEqual([1, 2, 3, 4])
    })

    it('lets an empty tick list stand for no restriction rather than for nothing', () => {
        expect(ids(filterExchanges(ALL, filter({inventoryIds: [], statuses: []})))).toEqual([1, 2, 3, 4, 5])
    })

    it('hides the finished exchanges when only the open ones are ticked', () => {
        expect(ids(filterExchanges(ALL, filter({statuses: openStatuses})))).toEqual([1, 3, 4, 5])
    })

    it('starts on the open exchanges', () => {
        expect(ids(filterExchanges(ALL, defaultExchangeFilter))).toEqual([1, 3, 4, 5])
    })

    it('narrows by all three at once rather than by one at a time', () => {
        const criteria = filter({search: 'anders', inventoryIds: ['2'], statuses: openStatuses})
        expect(ids(filterExchanges(ALL, criteria))).toEqual([4])
    })

    it('lets several ticks of one filter stand beside a name and a set of inventories', () => {
        const criteria = filter({
            search: 'anders',
            inventoryIds: ['1', '2'],
            statuses: [ExchangeStatus.SHIPPED, ExchangeStatus.ANNOUNCED],
        })
        expect(ids(filterExchanges(ALL, criteria))).toEqual([3, 4])
    })

    it('leaves nothing standing where the three contradict each other', () => {
        const criteria = filter({search: 'anders', inventoryIds: ['1'], statuses: [ExchangeStatus.DONE]})
        expect(filterExchanges(ALL, criteria)).toEqual([])
    })
})

describe('inventoryChoices', () => {
    it('offers only the inventories the loaded exchanges mention', () => {
        expect(inventoryChoices(ALL)).toEqual([
            {id: 1, name: 'Helme'},
            {id: 2, name: 'Jacken'},
            {id: 3, name: 'Stiefel'},
        ])
    })

    it('names an inventory once however many exchanges run in it', () => {
        expect(inventoryChoices([ANNA, CARLA])).toEqual([{id: 1, name: 'Helme'}])
    })
})

describe('exchangeComparators', () => {
    function sortedBy(key: keyof typeof exchangeComparators): number[] {
        return ids([...ALL].sort(exchangeComparators[key]))
    }

    it('orders by the member name shown on the row', () => {
        expect(sortedBy('member')).toEqual([1, 2, 3, 4, 5])
    })

    it('orders by the name of the inventory', () => {
        expect(sortedBy('inventory')).toEqual([1, 3, 2, 4, 5])
    })

    it('orders by the day the exchange was raised', () => {
        expect(sortedBy('date')).toEqual([1, 3, 2, 4, 5])
    })

    it('orders the status along the chain rather than by its word', () => {
        const shuffled: ExchangeStatusName[] = [
            ExchangeStatus.DONE,
            ExchangeStatus.RECEIVED,
            ExchangeStatus.ARRIVED,
            ExchangeStatus.ANNOUNCED,
            ExchangeStatus.SHIPPED,
        ]
        const rows = shuffled.map(status => row({
            id: statusChain.indexOf(status),
            name: 'Egal',
            ...HELMETS,
            status,
            createdAt: '2026-05-01T08:00:00Z',
        }))
        expect(ids([...rows].sort(exchangeComparators.status))).toEqual([0, 1, 2, 3, 4])
    })
})
