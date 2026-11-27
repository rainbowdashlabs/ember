/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import ItemMetadataPanel from './ItemMetadataPanel.vue'
import type {InventoryItem} from '@/api/generated/schema'

const calls = vi.hoisted(() => ({
    updateItem: vi.fn(),
    getInventory: vi.fn(),
    listArts: vi.fn(),
    itemTags: vi.fn(),
    listTags: vi.fn(),
    setItemTags: vi.fn(),
}))

vi.mock('@/api', () => ({
    inventory: {updateItem: calls.updateItem, getInventory: calls.getInventory},
    inventoryArts: {listArts: calls.listArts, ensureArt: vi.fn()},
    inventoryTags: {itemTags: calls.itemTags, listTags: calls.listTags, setItemTags: calls.setItemTags},
}))

vi.mock('../ItemTagPicker.vue', () => ({
    default: {name: 'ItemTagPicker', props: ['names', 'tags'], emits: ['update:names'], template: '<div/>'},
}))

vi.mock('../ArtPicker.vue', () => ({default: {name: 'ArtPicker', template: '<div/>'}}))

vi.mock('../useItemOwnerChoice', async () => {
    const {ref} = await import('vue')
    return {useItemOwnerChoice: () => ({
        owner: ref(null),
        offered: ref(false),
        atRest: ref(true),
        reset: () => undefined,
        apply: () => Promise.resolve(null),
    })}
})

const item = {id: 607, inventoryId: 22, name: 'Bierbank', metadata: null, artId: null} as unknown as InventoryItem

function panel() {
    return mount(ItemMetadataPanel, {
        props: {item, sizes: [], members: [], location: null, canEditItem: true},
        global: {stubs: {ItemOwnerField: true, 'router-link': true}},
    })
}

/** Tags are shown and changed on the piece's own page, not only in the dialog of its inventory. */
describe('ItemMetadataPanel tags', () => {
    it('shows the tags the piece carries', async () => {
        calls.listArts.mockResolvedValue([])
        calls.itemTags.mockResolvedValue([{id: 1, name: 'Zeltlager'}])
        const wrapper = panel()
        await flushPromises()

        expect(wrapper.text()).toContain('Zeltlager')
    })

    it('writes the tags back when the piece is saved', async () => {
        calls.listArts.mockResolvedValue([])
        calls.itemTags.mockResolvedValue([{id: 1, name: 'Zeltlager'}])
        calls.listTags.mockResolvedValue([])
        calls.updateItem.mockResolvedValue(item)
        calls.setItemTags.mockResolvedValue([{id: 1, name: 'Zeltlager'}, {id: 2, name: 'Fest'}])
        const wrapper = panel()
        await flushPromises()

        await wrapper.find('[data-testid="item-edit"]').trigger('click')
        wrapper.findComponent({name: 'ItemTagPicker'}).vm.$emit('update:names', ['Zeltlager', 'Fest'])
        await wrapper.find('[data-testid="item-edit-save"]').trigger('click')
        await flushPromises()

        expect(calls.setItemTags).toHaveBeenCalledWith(607, ['Zeltlager', 'Fest'])
        expect(wrapper.text()).toContain('Fest')
    })

    it('draws the piece with the picture of its kind before that of the inventory', async () => {
        calls.listArts.mockResolvedValue([{id: 3, name: 'Bierbank', icon: 'ph:park', color: '#15803d'}])
        calls.getInventory.mockResolvedValue({id: 22, icon: 'tent', color: null, homogeneous: false})
        calls.itemTags.mockResolvedValue([])
        const wrapper = mount(ItemMetadataPanel, {
            props: {item: {...item, artId: 3}, sizes: [], members: [], location: null, canEditItem: false},
            global: {stubs: {ItemOwnerField: true, 'router-link': true, GearGlyph: true}},
        })
        await flushPromises()

        expect(wrapper.findComponent({name: 'GearGlyph'}).props('glyph')).toEqual({icon: 'ph:park', color: '#15803d'})
    })

    it('shows the photo of the kind before that of the inventory', async () => {
        calls.listArts.mockResolvedValue([{id: 3, name: 'Bierbank', icon: null, color: null, pictureKey: 'kind-photo'}])
        calls.getInventory.mockResolvedValue({id: 22, icon: null, color: null, homogeneous: false, pictureKey: 'inventory-photo'})
        calls.itemTags.mockResolvedValue([])
        const wrapper = mount(ItemMetadataPanel, {
            props: {item: {...item, artId: 3}, sizes: [], members: [], location: null, canEditItem: false},
            global: {stubs: {ItemOwnerField: true, 'router-link': true, AuthImage: {props: ['src'], template: '<img :src="src">'}}},
        })
        await flushPromises()

        expect(wrapper.find('img').attributes('src')).toContain('v=kind-photo')
    })
})
