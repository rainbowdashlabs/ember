/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {nextTick} from 'vue'
import BaseBadge from './BaseBadge.vue'
import {useTheme} from '@/composables/useTheme'
import {DarkMode} from '@/theme/themes'

let painted = 'rgb(0, 0, 0)'

async function mountBadge() {
    const wrapper = mount(BaseBadge, {props: {bgClass: 'bg-primary/70'}})
    await nextTick()
    return wrapper
}

/**
 * A badge asks the browser what it painted and picks letters that can be read on it. The paint is
 * stubbed here because no stylesheet is loaded in a test, and stubbing it is also the only way to
 * say "the theme changed" in the terms the badge sees it.
 */
describe('BaseBadge', () => {
    beforeEach(() => {
        vi.spyOn(window, 'getComputedStyle').mockImplementation(
            () => ({backgroundColor: painted}) as CSSStyleDeclaration,
        )
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    it('takes light letters on a dark background and dark ones on a light background', async () => {
        painted = 'rgb(20, 20, 20)'
        expect((await mountBadge()).attributes('style')).toContain('#ffffff')

        painted = 'rgb(240, 240, 240)'
        expect((await mountBadge()).attributes('style')).toContain('#1a1a1a')
    })

    /** What a background with an opacity modifier on it comes back as. */
    it('reads a background the browser reports in oklab', async () => {
        painted = 'oklab(0.819776 -0.0234922 0.161686)'

        expect((await mountBadge()).attributes('style')).toContain('#1a1a1a')
    })

    it('follows the colours when the theme is switched under it', async () => {
        painted = 'rgb(20, 20, 20)'
        const wrapper = await mountBadge()
        expect(wrapper.attributes('style')).toContain('#ffffff')

        painted = 'rgb(240, 240, 240)'
        useTheme().applyDarkMode(DarkMode.LIGHT)
        await nextTick()

        expect(wrapper.attributes('style')).toContain('#1a1a1a')
    })
})
