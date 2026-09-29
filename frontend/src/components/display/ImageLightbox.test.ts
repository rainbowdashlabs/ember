/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import ImageLightbox from './ImageLightbox.vue'

enableAutoUnmount(afterEach)

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}}, missingWarn: false, fallbackWarn: false})

async function mountLightbox(props: Record<string, unknown> = {}, slot?: string) {
    const wrapper = mount(ImageLightbox, {
        props: {open: true, src: 'bild.png', alt: 'Ein Bild', ...props},
        slots: slot ? {default: slot} : {},
        global: {plugins: [i18n]},
        attachTo: document.body,
    })
    await flushPromises()
    return wrapper
}

function press(key: string) {
    const target = document.activeElement ?? document.body
    target.dispatchEvent(new KeyboardEvent('keydown', {key, bubbles: true, cancelable: true}))
}

describe('ImageLightbox', () => {
    it('is a dialog named after its picture', async () => {
        await mountLightbox()
        const dialog = document.querySelector('[role="dialog"]')
        const title = document.getElementById(dialog?.getAttribute('aria-labelledby') ?? '')
        expect(title?.textContent).toBe('Ein Bild')
        expect(dialog?.querySelector('img')?.getAttribute('src')).toBe('bild.png')
    })

    it('closes on escape', async () => {
        const wrapper = await mountLightbox()
        press('Escape')
        expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
    })

    /** One of several: the arrow keys ask for the neighbours, as far as there are any. */
    it('asks for the one before and after it with the arrow keys', async () => {
        const wrapper = await mountLightbox({position: {index: 0, count: 2}})

        press('ArrowLeft')
        press('ArrowRight')

        expect(wrapper.emitted('previous')).toBeUndefined()
        expect(wrapper.emitted('next')).toHaveLength(1)
    })

    it('shows what it is given in place of the picture', async () => {
        await mountLightbox({src: null}, '<table><tr><td>Zelle</td></tr></table>')
        const dialog = document.querySelector('[role="dialog"]')
        expect(dialog?.querySelector('img')).toBeNull()
        expect(dialog?.textContent).toContain('Zelle')
    })
})
