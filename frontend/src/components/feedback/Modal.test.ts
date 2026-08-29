/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {nextTick} from 'vue'
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

    /**
     * A dialog is answered from the keyboard: the button it is answered with holds the focus when
     * it opens, so the enter key alone is enough.
     */
    it('puts the focus on the button the dialog is answered with', async () => {
        const wrapper = mount(Modal, {
            props: {modelValue: false},
            slots: {default: '<button data-cancel>Abbrechen</button><button id="ok">Speichern</button>'},
            attachTo: document.body,
        })

        await wrapper.setProps({modelValue: true})
        await nextTick()

        expect(document.activeElement?.id).toBe('ok')
        wrapper.unmount()
    })

    /**
     * Where a dialog names its button outright, that one wins over the guess, whatever order the
     * buttons happen to be in.
     */
    it('prefers the button a dialog names over the last one', async () => {
        const wrapper = mount(Modal, {
            props: {modelValue: false},
            slots: {default: '<button id="named" data-confirm>Ja</button><button id="last">Nein</button>'},
            attachTo: document.body,
        })

        await wrapper.setProps({modelValue: true})
        await nextTick()

        expect(document.activeElement?.id).toBe('named')
        wrapper.unmount()
    })

    /**
     * Shift and enter answer it from anywhere inside, which is what a text field needs: there the
     * enter key belongs to the field.
     */
    it('answers on shift and enter from inside a text field', async () => {
        let answered = 0
        const wrapper = mount(Modal, {
            props: {modelValue: true},
            slots: {default: '<input id="text"><button id="ok">Speichern</button>'},
            attachTo: document.body,
        })
        document.querySelector('#ok')?.addEventListener('click', () => {
            answered++
        })

        document.querySelector('#text')?.dispatchEvent(
            new KeyboardEvent('keydown', {key: 'Enter', shiftKey: true, bubbles: true}))

        expect(answered, 'the dialog was answered').toBe(1)
        wrapper.unmount()
    })

    it('leaves a plain enter to whatever has the focus', async () => {
        let answered = 0
        const wrapper = mount(Modal, {
            props: {modelValue: true},
            slots: {default: '<input id="text"><button id="ok">Speichern</button>'},
            attachTo: document.body,
        })
        document.querySelector('#ok')?.addEventListener('click', () => {
            answered++
        })

        document.querySelector('#text')?.dispatchEvent(
            new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}))

        expect(answered).toBe(0)
        wrapper.unmount()
    })
})
