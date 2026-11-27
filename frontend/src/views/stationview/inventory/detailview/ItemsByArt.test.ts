/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h} from 'vue'
import type {InventoryArt, InventoryItem} from '@/api/generated/schema'
import {useItemTable} from '../itemtable/useItemTable'
import ItemsByArt from './ItemsByArt.vue'

vi.mock('@/api/inventoryFields', () => ({listFields: async () => []}))
vi.mock('@/api/inventoryTags', () => ({inventoryItemTags: async () => []}))

const ITEMS = [
    {id: 1, inventoryId: 22, name: 'Bierbank', artId: 3},
    {id: 2, inventoryId: 22, name: 'Biertisch', artId: 4},
    {id: 3, inventoryId: 22, name: 'Kabeltrommel', artId: null},
] as unknown as InventoryItem[]

const ARTS = [
    {id: 3, name: 'Bänke', icon: 'ph:park', color: '#15803d'},
    {id: 4, name: 'Tische', icon: null, color: null},
] as unknown as InventoryArt[]

async function headings(inventoryGlyph: {icon: string | null, color: string | null}) {
    const wrapper = mount(defineComponent({
        setup() {
            const table = useItemTable({
                inventoryId: () => 22,
                items: () => ITEMS,
                sizes: () => [],
                hasSizes: () => false,
                isMixed: () => false,
                sizeLabel: () => '',
                assignedName: () => '',
            })
            return () => h(ItemsByArt, {items: table, arts: ARTS, inventoryGlyph, memberIdentity: () => null,
                lentItemStationMap: new Map(), containerPathById: new Map()})
        },
    }), {global: {stubs: {InventoryItemTable: true, TableFilterDialog: true, RecordCardControls: true, GearGlyph: true}}})
    await flushPromises()
    return wrapper.findAllComponents({name: 'GearGlyph'}).map(glyph => glyph.props('glyph'))
}

/** Each kind heading is drawn with the kind's picture, falling back to the inventory's. */
describe('ItemsByArt headings', () => {
    beforeEach(() => localStorage.clear())

    it('draws a kind by its own picture and the others by the inventory', async () => {
        expect(await headings({icon: 'tent', color: '#2563eb'})).toEqual([
            {icon: 'ph:park', color: '#15803d'},
            {icon: 'tent', color: '#2563eb'},
            {icon: 'tent', color: '#2563eb'},
        ])
    })

    it('draws the loose pieces as a box where the inventory has no picture', async () => {
        const glyphs = await headings({icon: null, color: null})

        expect(glyphs.map(glyph => glyph.icon)).toEqual(['ph:park', 'box', 'box'])
    })

    it('shows the photo of a kind that has one in place of its icon', async () => {
        const arts = [{...ARTS[0], pictureKey: 'bench-photo'}, ARTS[1]] as unknown as InventoryArt[]
        const wrapper = mount(defineComponent({
            setup() {
                const table = useItemTable({
                    inventoryId: () => 22,
                    items: () => ITEMS,
                    sizes: () => [],
                    hasSizes: () => false,
                    isMixed: () => false,
                    sizeLabel: () => '',
                    assignedName: () => '',
                })
                return () => h(ItemsByArt, {items: table, arts, inventoryGlyph: {icon: 'tent', color: null},
                    memberIdentity: () => null, lentItemStationMap: new Map(), containerPathById: new Map()})
            },
        }), {global: {stubs: {InventoryItemTable: true, TableFilterDialog: true, RecordCardControls: true,
            GearGlyph: true, AuthImage: {props: ['src'], template: '<img :src="src">'}}}})
        await flushPromises()

        expect(wrapper.find('img').attributes('src')).toContain('v=bench-photo')
        expect(wrapper.findAllComponents({name: 'GearGlyph'})).toHaveLength(2)
    })
})
