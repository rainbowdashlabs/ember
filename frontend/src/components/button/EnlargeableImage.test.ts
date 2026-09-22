/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, describe, expect, it} from 'vitest'
import {mount, type VueWrapper} from '@vue/test-utils'
import EnlargeableImage from './EnlargeableImage.vue'

/**
 * A picture that opens large on a click and closes again on the close button, a click beside it or
 * Escape.
 */
describe('EnlargeableImage', () => {
    let view: VueWrapper | undefined

    afterEach(() => {
        view?.unmount()
        document.body.innerHTML = ''
    })

    function picture() {
        view = mount(EnlargeableImage, {
            props: {src: '/large.webp', alt: 'Wappen', caption: 'Am Tor'},
            slots: {default: '<img src="/small.webp" alt="Wappen"/>'},
            attachTo: document.body,
        })
        return view
    }

    function overlay(): HTMLElement | null {
        return document.body.querySelector('[role="dialog"]')
    }

    it('opens the larger copy with its caption on a click', async () => {
        await picture().find('button').trigger('click')

        expect(overlay()?.querySelector('img')?.getAttribute('src')).toBe('/large.webp')
        expect(overlay()?.textContent).toContain('Am Tor')
    })

    it('closes on Escape', async () => {
        await picture().find('button').trigger('click')

        window.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))
        await view!.vm.$nextTick()

        expect(overlay()).toBeNull()
    })

    it('closes on a click beside the picture but not on the picture', async () => {
        await picture().find('button').trigger('click')

        overlay()!.querySelector('img')!.dispatchEvent(new MouseEvent('click', {bubbles: true}))
        await view!.vm.$nextTick()
        expect(overlay()).not.toBeNull()

        overlay()!.dispatchEvent(new MouseEvent('click', {bubbles: true}))
        await view!.vm.$nextTick()
        expect(overlay()).toBeNull()
    })
})
