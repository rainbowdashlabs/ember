/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import EnlargeableImage from './EnlargeableImage.vue'

/**
 * A picture that opens large on a click and closes again on the close button, a press beside it or
 * Escape.
 */
describe('EnlargeableImage', () => {
    let view: VueWrapper | undefined

    afterEach(() => {
        view?.unmount()
        document.body.innerHTML = ''
    })

    /** Opens the picture and waits until it listens for presses beside it, which starts a timer later. */
    async function openPicture() {
        view = mount(EnlargeableImage, {
            props: {src: '/large.webp', alt: 'Wappen', caption: 'Am Tor'},
            slots: {default: '<img src="/small.webp" alt="Wappen"/>'},
            attachTo: document.body,
        })
        await view.find('button').trigger('click')
        await flushPromises()
        await new Promise(resolve => setTimeout(resolve, 0))
    }

    function dialog(): HTMLElement | null {
        return document.body.querySelector('[role="dialog"]')
    }

    function pressOn(target: Element) {
        target.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true, button: 0}))
    }

    it('opens the larger copy with its caption on a click', async () => {
        await openPicture()

        expect(dialog()?.querySelector('img')?.getAttribute('src')).toBe('/large.webp')
        expect(dialog()?.textContent).toContain('Am Tor')
    })

    it('closes on Escape', async () => {
        await openPicture()

        document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}))
        await flushPromises()

        expect(dialog()).toBeNull()
    })

    it('closes on a press beside the picture but not on the picture', async () => {
        await openPicture()

        pressOn(dialog()!.querySelector('img')!)
        await flushPromises()
        expect(dialog()).not.toBeNull()

        pressOn(dialog()!.parentElement!)
        await flushPromises()
        expect(dialog()).toBeNull()
    })
})
