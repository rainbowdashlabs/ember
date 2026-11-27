/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import SaveButton from '@/components/button/SaveButton.vue'
import PartnerDescriptionField from '@/components/inventory/PartnerDescriptionField.vue'
import InventoryPictureField from '@/components/inventory/InventoryPictureField.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ItemPresentationPanel from './ItemPresentationPanel.vue'

const getInventory = vi.fn()
const getItemPresentation = vi.fn()
const describeItem = vi.fn()
vi.mock('@/api', () => ({
    inventory: {
        getInventory: (id: number) => getInventory(id),
        getItemPresentation: (id: number) => getItemPresentation(id),
        describeItem: (id: number, text: string) => describeItem(id, text),
    },
    inventoryPictures: {
        itemPictureUrl: () => '/picture',
        uploadItemPicture: vi.fn(),
        removeItemPicture: vi.fn(),
    },
}))

async function panel(homogeneous: boolean) {
    getInventory.mockResolvedValue({id: 3, homogeneous})
    const wrapper = shallowMount(ItemPresentationPanel, {
        props: {itemId: 14, inventoryId: 3},
        global: {renderStubDefaultSlot: true},
    })
    await flushPromises()
    return wrapper
}

/** A piece carries text and a photo of its own only where its inventory lends pieces one by one. */
describe('ItemPresentationPanel', () => {
    beforeEach(() => {
        getItemPresentation.mockReset().mockResolvedValue({itemId: 14, description: 'Vollständig', pictureKey: null})
        describeItem.mockReset().mockResolvedValue({itemId: 14, description: 'Mit Erweiterung', pictureKey: null})
    })

    it('stays away for an inventory of copies', async () => {
        expect((await panel(true)).find('[data-testid="item-presentation"]').exists()).toBe(false)
    })

    it('shows a failed load instead of staying silent', async () => {
        getItemPresentation.mockReset().mockRejectedValue(new Error('down'))

        expect((await panel(false)).findComponent(FailureAlert).exists()).toBe(true)
    })

    it('passes a new photo on, so the header shows it at once', async () => {
        const wrapper = await panel(false)

        wrapper.findComponent(InventoryPictureField).vm.$emit('changed', 'own-key')

        expect(wrapper.emitted('pictureChanged')).toEqual([['own-key']])
    })

    it('saves the text of a piece of an inventory of different things', async () => {
        const wrapper = await panel(false)
        expect(wrapper.findComponent(PartnerDescriptionField).props('modelValue')).toBe('Vollständig')

        wrapper.findComponent(PartnerDescriptionField).vm.$emit('update:modelValue', 'Mit Erweiterung')
        await wrapper.vm.$nextTick()
        await wrapper.findComponent(SaveButton).props('action')()

        expect(describeItem).toHaveBeenCalledWith(14, 'Mit Erweiterung')
        expect(wrapper.findComponent(SaveButton).props('disabled')).toBe(true)
    })
})
