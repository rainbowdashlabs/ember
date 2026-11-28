/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import BatchArtModal from './BatchArtModal.vue'
import BatchTagModal from './BatchTagModal.vue'

const calls = vi.hoisted(() => ({
    assignArt: vi.fn(),
    ensureArt: vi.fn(),
    listTags: vi.fn(),
    changeTagsOfItems: vi.fn(),
}))

vi.mock('@/api', () => ({
    inventoryArts: {assignArt: calls.assignArt, ensureArt: calls.ensureArt},
    inventoryTags: {listTags: calls.listTags, changeTagsOfItems: calls.changeTagsOfItems},
}))

vi.mock('../../ItemTagPicker.vue', () => ({
    default: {name: 'ItemTagPicker', props: ['names', 'tags', 'creatable'], emits: ['update:names'], template: '<div/>'},
}))

vi.mock('../../ArtPicker.vue', () => ({
    default: {name: 'ArtPicker', props: ['artId', 'draft', 'arts'], emits: ['update:artId', 'update:draft'], template: '<div/>'},
}))

const stubs = {Modal: {template: '<div><slot/></div>'}}

/** The dialogs of the select mode hand every ticked piece to one request. */
describe('batch dialogs', () => {
    it('puts the ticked pieces under the kind picked', async () => {
        calls.assignArt.mockResolvedValue({changed: 2})
        const wrapper = mount(BatchArtModal, {
            props: {modelValue: false, inventoryId: 22, arts: [], itemIds: [4, 5]},
            global: {stubs},
        })
        await wrapper.setProps({modelValue: true})
        wrapper.findComponent({name: 'ArtPicker'}).vm.$emit('update:artId', 9)
        await wrapper.find('[data-testid="batch-art-apply"]').trigger('click')
        await flushPromises()

        expect(calls.assignArt).toHaveBeenCalledWith(22, 9, [4, 5])
        expect(wrapper.emitted('done')).toEqual([[2]])
    })

    it('takes the kind away when none is picked', async () => {
        calls.assignArt.mockResolvedValue({changed: 1})
        const wrapper = mount(BatchArtModal, {
            props: {modelValue: false, inventoryId: 22, arts: [], itemIds: [4]},
            global: {stubs},
        })
        await wrapper.setProps({modelValue: true})
        await wrapper.find('[data-testid="batch-art-apply"]').trigger('click')
        await flushPromises()

        expect(calls.assignArt).toHaveBeenCalledWith(22, null, [4])
    })

    it('writes down a kind typed in and puts the pieces under it', async () => {
        calls.ensureArt.mockResolvedValue(31)
        calls.assignArt.mockResolvedValue({changed: 2})
        const wrapper = mount(BatchArtModal, {
            props: {modelValue: false, inventoryId: 22, arts: [], itemIds: [4, 5]},
            global: {stubs},
        })
        await wrapper.setProps({modelValue: true})
        wrapper.findComponent({name: 'ArtPicker'}).vm.$emit('update:draft', 'Bierbank')
        await wrapper.find('[data-testid="batch-art-apply"]').trigger('click')
        await flushPromises()

        expect(calls.ensureArt).toHaveBeenCalledWith(22, [], 'Bierbank')
        expect(calls.assignArt).toHaveBeenCalledWith(22, 31, [4, 5])
    })

    it('adds the words picked to every ticked piece', async () => {
        calls.listTags.mockResolvedValue([])
        calls.changeTagsOfItems.mockResolvedValue([])
        const wrapper = mount(BatchTagModal, {
            props: {modelValue: false, inventoryId: 22, itemIds: [4, 5], mode: 'add'},
            global: {stubs},
        })
        await wrapper.setProps({modelValue: true})
        await flushPromises()
        wrapper.findComponent({name: 'ItemTagPicker'}).vm.$emit('update:names', ['Festzelt'])
        await wrapper.vm.$nextTick()
        await wrapper.find('[data-testid="batch-tag-apply"]').trigger('click')
        await flushPromises()

        expect(calls.changeTagsOfItems).toHaveBeenCalledWith(22, {itemIds: [4, 5], add: ['Festzelt'], remove: []})
        expect(wrapper.emitted('done')).toHaveLength(1)
    })

    it('takes the words picked off and offers no new word', async () => {
        calls.listTags.mockResolvedValue([])
        calls.changeTagsOfItems.mockResolvedValue([])
        const wrapper = mount(BatchTagModal, {
            props: {modelValue: false, inventoryId: 22, itemIds: [4], mode: 'remove'},
            global: {stubs},
        })
        await wrapper.setProps({modelValue: true})
        await flushPromises()
        const picker = wrapper.findComponent({name: 'ItemTagPicker'})
        expect(picker.props('creatable')).toBe(false)
        picker.vm.$emit('update:names', ['Alt'])
        await wrapper.vm.$nextTick()
        await wrapper.find('[data-testid="batch-tag-apply"]').trigger('click')
        await flushPromises()

        expect(calls.changeTagsOfItems).toHaveBeenCalledWith(22, {itemIds: [4], add: [], remove: ['Alt']})
    })
})
