/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {CartLineCheck, CartLineView} from '@/api/generated/schema'
import {checkoutBlock, groupByStation} from './cartGroups'

function line(id: number, stationId: string, stationName = stationId): CartLineView {
    return {id, stationId, stationName, inventoryId: 7, artId: null, itemId: null, quantity: 1, inventoryName: 'Funk', lineName: '', pictureKey: null}
}

function checks(...states: [number, CartLineCheck['state']][]): Map<number, CartLineCheck> {
    return new Map(states.map(([lineId, state]) => [lineId, {lineId, free: 1, state}]))
}

/** A cart goes out as one request per station, and only once every line is free on a chosen day. */
describe('cartGroups', () => {
    it('groups lines by station in the order the stations were first put in', () => {
        const groups = groupByStation([line(1, 'b', 'Süd'), line(2, 'a', 'Nord'), line(3, 'b', 'Süd')])

        expect(groups.map(group => [group.stationName, group.lines.map(l => l.id)])).toEqual([['Süd', [1, 3]], ['Nord', [2]]])
    })

    it('holds the cart back while it is empty, has no day, is unchecked or not free in full', () => {
        const lines = [line(1, 'a'), line(2, 'a')]

        expect(checkoutBlock([], '2030-06-12', checks())).toBe('empty')
        expect(checkoutBlock(lines, null, checks([1, 'FREE'], [2, 'FREE']))).toBe('noDay')
        expect(checkoutBlock(lines, '2030-06-12', null)).toBe('unchecked')
        expect(checkoutBlock(lines, '2030-06-12', checks([1, 'FREE']))).toBe('unchecked')
        expect(checkoutBlock(lines, '2030-06-12', checks([1, 'FREE'], [2, 'SHORT']))).toBe('notFree')
        expect(checkoutBlock(lines, '2030-06-12', checks([1, 'FREE'], [2, 'FREE']))).toBeNull()
    })
})
