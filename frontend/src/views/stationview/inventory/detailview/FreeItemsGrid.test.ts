/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FreeItemsGrid from './FreeItemsGrid.vue'
import type {InventoryArt, InventoryItem} from '@/api/generated/schema'

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

    it('shows the kind a piece is sorted into', () => {
        const art = {id: 3, name: 'Winterjacke'} as InventoryArt
        const wrapper = mount(FreeItemsGrid, {
            props: {
                items: [{...piece(57), artId: 3}, piece(58)],
                sizes: [],
                arts: [art],
                containerPathById: new Map(),
            },
            global: {stubs: {'router-link': RouterLink}},
        })

        expect(wrapper.text()).toContain('Winterjacke')
        expect(wrapper.text().match(/Winterjacke/g)).toHaveLength(1)
    })

    it('draws a piece with the picture of its kind before that of the inventory', () => {
        const art = {id: 3, name: 'Bierbank', icon: 'ph:park', color: null} as InventoryArt
        const wrapper = mount(FreeItemsGrid, {
            props: {
                items: [{...piece(57), artId: 3}, piece(58)],
                sizes: [],
                arts: [art],
                inventoryGlyph: {icon: 'tent', color: null, homogeneous: false},
                containerPathById: new Map(),
            },
            global: {stubs: {'router-link': RouterLink, GearGlyph: true}},
        })

        const glyphs = wrapper.findAllComponents({name: 'GearGlyph'}).map(glyph => glyph.props('glyph').icon)
        expect(glyphs).toEqual(['ph:park', 'tent'])
    })
})
