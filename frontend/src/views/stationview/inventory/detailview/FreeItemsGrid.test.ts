/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FreeItemsGrid from './FreeItemsGrid.vue'
import type {InventoryItem} from '@/api/generated/schema'

const RouterLink = {template: '<a><slot/></a>', props: ['to']}

function piece(id: number): InventoryItem {
    return {id, inventoryId: 5, name: `Sporttasche ${id}`, assignedTo: null, lostAt: null} as InventoryItem
}

/**
 * A promised piece is free on the shelf and spoken for all the same, so it names its movement rather
 * than offering a hand-out that would be refused.
 */
describe('FreeItemsGrid', () => {
    it('marks a piece an open movement carries instead of offering it', () => {
        const wrapper = mount(FreeItemsGrid, {
            props: {
                items: [piece(57), piece(58)],
                sizes: [],
                containerPathById: new Map(),
                movementItemMap: new Map([[57, 78]]),
            },
            global: {stubs: {'router-link': RouterLink}},
        })

        expect(wrapper.findAll('[data-testid="movement-piece-badge"]')).toHaveLength(1)
        expect(wrapper.findAll('button'), 'only the free one offers a hand-out').toHaveLength(1)
    })
})
