/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import Modal from './Modal.vue'

/**
 * The dialog behind dozens of screens. Its content is teleported to the document body, which the
 * stub undoes so the assertions can look at it where it was declared.
 */
function mountModal(open: boolean) {
    return mount(Modal, {
        props: {modelValue: open},
        slots: {default: '<p>Inhalt</p>'},
        global: {stubs: {teleport: true}},
    })
}

describe('Modal', () => {
    it('shows its content when open', () => {
        expect(mountModal(true).text()).toContain('Inhalt')
    })

    it('shows nothing when closed', () => {
        expect(mountModal(false).text()).not.toContain('Inhalt')
    })

    it('reports the close when the close button is used', async () => {
        const wrapper = mountModal(true)
        await wrapper.find('button').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('reports the close when the backdrop is clicked', async () => {
        const wrapper = mountModal(true)
        await wrapper.find('.absolute.inset-0').trigger('click')
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })
})
