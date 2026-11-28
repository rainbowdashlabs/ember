/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {CalendarBar, LendingStatus} from '@/api/generated/schema'
import {barsOf, blocksOf, clashDays, columnsOf, daysFrom, dragSpan, placeBars} from './calendarGrid'

const DAYS = daysFrom('2030-06-01', 7)

function bar(requestId: number, from: string, to: string, quantity = 1, status: LendingStatus = 'APPROVED', artId: number | null = null): CalendarBar {
    return {requestId, inventoryId: 1, artId, partner: 'Nord', quantity, status, overdue: false, from, to}
}

/** How the calendar lays requests and blocks onto its days. */
describe('calendarGrid', () => {
    it('clips a span to the window and leaves out what lies outside', () => {
        expect(columnsOf('2030-05-28', '2030-06-02', DAYS)).toEqual({start: 1, end: 2})
        expect(columnsOf('2030-06-03', '2030-06-20', DAYS)).toEqual({start: 3, end: 7})
        expect(columnsOf('2030-06-10', '2030-06-12', DAYS)).toBeNull()
    })

    it('puts overlapping bars in separate lanes and reuses a lane once it is free', () => {
        const placed = placeBars([bar(1, '2030-06-01', '2030-06-03'), bar(2, '2030-06-02', '2030-06-04'), bar(3, '2030-06-05', '2030-06-06')], DAYS)

        expect(placed.map(p => [p.bar.requestId, p.lane])).toEqual([[1, 0], [2, 1], [3, 0]])
    })

    it('marks the days where open and approved requests ask for more than there is', () => {
        const bars = [bar(1, '2030-06-01', '2030-06-02', 2), bar(2, '2030-06-02', '2030-06-03', 2, 'REQUESTED'), bar(3, '2030-06-02', '2030-06-02', 5, 'RETURNED'),
            bar(4, '2030-06-05', '2030-06-05', 3, 'LENT'), bar(5, '2030-06-05', '2030-06-05', 1, 'REQUESTED')]

        expect([...clashDays(bars, {inventoryId: 1, artId: null, pieceCount: 3}, DAYS)]).toEqual(['2030-06-02', '2030-06-05'])
    })

    it('marks a product row where one of its kinds asks for more than the kind holds', () => {
        const bars = [bar(1, '2030-06-03', '2030-06-03', 3, 'REQUESTED', 5)]
        const kinds = [{inventoryId: 1, artId: 5, pieceCount: 2}, {inventoryId: 1, artId: 6, pieceCount: 10}]

        expect([...clashDays(bars, {inventoryId: 1, artId: null, pieceCount: 12}, DAYS)]).toEqual([])
        expect([...clashDays(bars, {inventoryId: 1, artId: null, pieceCount: 12}, DAYS, kinds)]).toEqual(['2030-06-03'])
    })

    it('gives a kind row only its lines, a product row every block of it and the station', () => {
        expect(barsOf([bar(1, '2030-06-01', '2030-06-01', 1, 'APPROVED', 5), bar(2, '2030-06-01', '2030-06-01')], {inventoryId: 1, artId: 5, pieceCount: 1}).map(b => b.requestId)).toEqual([1])
        const block = (id: number, inventoryId: number | null, itemId: number | null = null, artId: number | null = null) =>
            ({id, inventoryId, artId, itemId, pieceName: null, from: '2030-06-01', to: '2030-06-01', reason: ''})
        const blocks = [block(1, 1), block(2, null), block(3, 2), block(4, 1, 9, 5), block(5, 1, 8, 6), block(6, null, 7)]
        expect(blocksOf(blocks, {inventoryId: 1, artId: null}).map(b => b.id)).toEqual([1, 2, 4, 5])
        expect(blocksOf(blocks, {inventoryId: 1, artId: 5}).map(b => b.id)).toEqual([1, 2, 4])
        expect(dragSpan('2030-06-05', '2030-06-02')).toEqual({from: '2030-06-02', to: '2030-06-05'})
    })
})
