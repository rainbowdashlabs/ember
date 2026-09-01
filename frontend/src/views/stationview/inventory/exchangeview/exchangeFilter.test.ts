/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {ExchangeStatus, type ExchangeRequestEntry, type ExchangeStatusName} from '@/api/exchanges'
import {
    ALL_STATUSES,
    OPEN_STATUSES,
    defaultExchangeFilter,
    exchangeComparators,
    filterExchanges,
    inventoryChoices,
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

const ANNA = row({id: 1, name: 'Anna Berger', ...HELMETS, status: ExchangeStatus.ANNOUNCED, createdAt: '2026-05-01T08:00:00Z'})
const BENNO = row({id: 2, name: 'Benno Klein', ...JACKETS, status: ExchangeStatus.DONE, createdAt: '2026-05-03T08:00:00Z'})
const CARLA = row({id: 3, name: 'Carla Anders', ...HELMETS, status: ExchangeStatus.SHIPPED, createdAt: '2026-05-02T08:00:00Z'})
const DORA = row({id: 4, name: 'Dora Anders', ...JACKETS, status: ExchangeStatus.ANNOUNCED, createdAt: '2026-05-04T08:00:00Z'})

const ALL = [ANNA, BENNO, CARLA, DORA]

function ids(rows: ExchangeRequestEntry[]): number[] {
    return rows.map(r => r.id)
}

function filter(overrides: Partial<ExchangeFilter> = {}): ExchangeFilter {
    return {search: '', inventoryId: '', status: ALL_STATUSES, ...overrides}
}

describe('filterExchanges', () => {
    it('keeps everything when nothing is asked for', () => {
        expect(ids(filterExchanges(ALL, filter()))).toEqual([1, 2, 3, 4])
    })

    it('matches part of a member name without regard to case', () => {
        expect(ids(filterExchanges(ALL, filter({search: 'anders'})))).toEqual([3, 4])
        expect(ids(filterExchanges(ALL, filter({search: '  BERG  '})))).toEqual([1])
    })

    it('keeps only the exchanges of one inventory', () => {
        expect(ids(filterExchanges(ALL, filter({inventoryId: '2'})))).toEqual([2, 4])
    })

    it('keeps only one status when one is named', () => {
        expect(ids(filterExchanges(ALL, filter({status: ExchangeStatus.ANNOUNCED})))).toEqual([1, 4])
    })

    it('hides the finished exchanges when only the open ones are asked for', () => {
        expect(ids(filterExchanges(ALL, filter({status: OPEN_STATUSES})))).toEqual([1, 3, 4])
    })

    it('starts on the open exchanges', () => {
        expect(ids(filterExchanges(ALL, defaultExchangeFilter))).toEqual([1, 3, 4])
    })

    it('narrows by all three at once rather than by one at a time', () => {
        const criteria = filter({search: 'anders', inventoryId: '2', status: OPEN_STATUSES})
        expect(ids(filterExchanges(ALL, criteria))).toEqual([4])
    })

    it('leaves nothing standing where the three contradict each other', () => {
        const criteria = filter({search: 'anders', inventoryId: '1', status: ExchangeStatus.DONE})
        expect(filterExchanges(ALL, criteria)).toEqual([])
    })
})

describe('inventoryChoices', () => {
    it('offers only the inventories the loaded exchanges mention', () => {
        expect(inventoryChoices(ALL)).toEqual([
            {id: 1, name: 'Helme'},
            {id: 2, name: 'Jacken'},
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
        expect(sortedBy('member')).toEqual([1, 2, 3, 4])
    })

    it('orders by the name of the inventory', () => {
        expect(sortedBy('inventory')).toEqual([1, 3, 2, 4])
    })

    it('orders by the day the exchange was raised', () => {
        expect(sortedBy('date')).toEqual([1, 3, 2, 4])
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
