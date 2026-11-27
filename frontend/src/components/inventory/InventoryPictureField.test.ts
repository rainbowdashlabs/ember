/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import InventoryPictureField from './InventoryPictureField.vue'

const stubs = {AuthImage: true, Spinner: true, FailureAlert: true}

/** The photo field sends a picked photo straight away, replaces one in one step, and takes it away. */
describe('InventoryPictureField', () => {
    it('sends a picked photo and hands its new key on', async () => {
        const upload = vi.fn().mockResolvedValue('new-key')
        const wrapper = mount(InventoryPictureField, {
            props: {src: null, upload, remove: vi.fn()},
            global: {stubs},
        })
        const file = new File(['x'], 'burg.png', {type: 'image/png'})
        const input = wrapper.find('input[type="file"]')
        Object.defineProperty(input.element, 'files', {value: [file]})
        await input.trigger('change')
        await flushPromises()

        expect(upload).toHaveBeenCalledWith(file)
        expect(wrapper.emitted('changed')).toEqual([['new-key']])
        expect(wrapper.find('[data-testid="inventory-picture-remove"]').exists()).toBe(false)
    })

    it('offers replacing and removing a photo that is there', async () => {
        const remove = vi.fn().mockResolvedValue(undefined)
        const wrapper = mount(InventoryPictureField, {
            props: {src: '/picture', upload: vi.fn(), remove},
            global: {stubs},
        })

        expect(wrapper.find('[data-testid="inventory-picture-pick"]').text()).toContain('Foto ersetzen')
        await wrapper.find('[data-testid="inventory-picture-remove"]').trigger('click')
        await flushPromises()

        expect(remove).toHaveBeenCalled()
        expect(wrapper.emitted('changed')).toEqual([[null]])
    })
})
