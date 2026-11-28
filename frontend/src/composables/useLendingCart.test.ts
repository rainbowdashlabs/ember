/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {CartLineView, CartView} from '@/api/generated/schema'
import {useLendingCart} from './useLendingCart'

const addCartLine = vi.fn()
const getCart = vi.fn()
vi.mock('@/api/lendingCart', () => ({
    addCartLine: (line: unknown) => addCartLine(line),
    getCart: () => getCart(),
}))
const getSidebarCounts = vi.fn()
vi.mock('@/api/sidebar', () => ({getSidebarCounts: () => getSidebarCounts()}))

function line(id: number, stationId: string, inventoryId: number, quantity: number): CartLineView {
    return {id, stationId, stationName: stationId, inventoryId, artId: null, itemId: null, quantity, inventoryName: '', lineName: '', pictureKey: null}
}

function view(lines: CartLineView[]): CartView {
    return {dateFrom: null, dateTo: null, occasion: '', note: '', lines}
}

/** The one cart the catalog, the product pages and the cart page share. */
describe('useLendingCart', () => {
    beforeEach(() => {
        getSidebarCounts.mockReset().mockResolvedValue({})
        getCart.mockReset().mockResolvedValue(view([line(1, 'a', 7, 2), line(2, 'a', 7, 1), line(3, 'b', 8, 1)]))
        addCartLine.mockReset()
    })

    it('sums what it holds of a product and knows its stations', async () => {
        const cart = useLendingCart()
        await cart.load()

        expect(cart.shareOf('a', 7)?.quantity).toBe(3)
        expect(cart.shareOf('b', 7)).toBeUndefined()
        expect([...cart.stations.value]).toEqual(['a', 'b'])
    })

    it('takes the answer of a change as the cart and reads the sidebar counts again', async () => {
        const cart = useLendingCart()
        addCartLine.mockResolvedValue(view([line(9, 'c', 1, 1)]))

        await cart.add({lendingStationUid: 'c', inventoryId: 1, artId: null, itemId: null, quantity: 1})

        expect(useLendingCart().lines.value.map(entry => entry.id)).toEqual([9])
        expect(getSidebarCounts).toHaveBeenCalled()
    })
})
