/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import InventoryItemCard from './InventoryItemCard.vue'
import type {MyInventoryItem} from '@/api/generated/schema'

function card(over: Partial<MyInventoryItem>) {
    const item = {
        id: 5,
        name: 'Jacke',
        inventoryId: 1,
        inventoryName: 'Jacken',
        inventoryHomogeneous: true,
        custody: 'WITH_MEMBER',
        ownerKind: 'STATION',
        lostAt: null,
        movement: null,
        ...over,
    } as MyInventoryItem
    return mount(InventoryItemCard, {props: {item, showExchangeButton: true}, global: {stubs: {ItemCardSummary: true}}})
}

/** A missing piece is replaced through a loss report, so the member is not offered a swap of it. */
describe('InventoryItemCard', () => {
    it('offers a swap of a piece the member holds', () => {
        expect(card({}).find('[data-testid="item-request-exchange"]').exists()).toBe(true)
    })

    it('offers no swap of a piece reported missing', () => {
        const missing = card({custody: 'LOST', lostAt: '2026-05-01T08:00:00Z'})

        expect(missing.find('[data-testid="item-request-exchange"]').exists()).toBe(false)
    })
})
