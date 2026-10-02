/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {defineComponent, h, nextTick} from 'vue'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {useBreakpoint} from './useBreakpoint'
import {useFinePointer} from './useFinePointer'

/** A browser that answers yes to every media query: a phone with a mouse, which is all the test needs. */
function everyQueryMatches() {
    vi.spyOn(window, 'matchMedia').mockImplementation(query => ({
        matches: true,
        media: query,
        onchange: null,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        dispatchEvent: () => false,
    }))
}

/** Mounts a component that notes what the device composables said while it was set up. */
function mountReader() {
    const seen: {phone: boolean, mouse: boolean}[] = []
    let read = () => seen[0]!
    mount(defineComponent({
        setup() {
            const {isMobile} = useBreakpoint()
            const {finePointer} = useFinePointer()
            read = () => ({phone: isMobile.value, mouse: finePointer.value})
            seen.push(read())
            return () => h('p')
        },
    }))
    return {atSetup: seen[0]!, now: read}
}

/**
 * The device composables in the browser. What the server rendered knows nothing of the device, so
 * while a page it sent is taken up they have to agree with it, and only afterwards say what the
 * browser knows.
 */
describe('device facts', () => {
    beforeEach(everyQueryMatches)

    afterEach(() => {
        useNuxtApp().isHydrating = false
        vi.restoreAllMocks()
    })

    it('holds them back until mounted while a server-rendered page is taken up', async () => {
        useNuxtApp().isHydrating = true

        const reader = mountReader()
        await nextTick()

        expect(reader.atSetup).toEqual({phone: false, mouse: false})
        expect(reader.now()).toEqual({phone: true, mouse: true})
    })

    it('gives them straight away on a page the browser renders alone', () => {
        expect(mountReader().atSetup).toEqual({phone: true, mouse: true})
    })
})
