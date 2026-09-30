/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import AppFooter from './AppFooter.vue'

vi.mock('@/api/client', () => ({
    default: {get: async () => ({data: {version: '26.20.0'}})},
}))

/**
 * The footer on a phone still leads to the discovery page.
 *
 * @vitest-environment happy-dom
 *
 * <p>No stylesheet runs here, so the test reads what decides visibility at a breakpoint: a `hidden`
 * class on the link or on anything around it inside the footer, which is what kept the link off
 * every screen narrower than the `md` breakpoint.
 */
describe('AppFooter', () => {
    const RouterLink = {props: ['to'], template: '<a v-bind="$attrs"><slot/></a>'}

    beforeEach(() => {
        window.innerWidth = 375
        window.dispatchEvent(new Event('resize'))
    })

    function footer() {
        return mount(AppFooter, {
            slots: {default: '<span data-testid="switcher">Wache wechseln</span>'},
            global: {
                stubs: {
                    RouterLink,
                    'router-link': RouterLink,
                    ClientOnly: {template: '<div><slot/></div>'},
                    UpdateNotice: true,
                    ThemeToggle: true,
                },
            },
        })
    }

    function hiddenOnPhones(element: Element | null, root: Element): boolean {
        for (let node = element; node && node !== root.parentElement; node = node.parentElement) {
            if (node.classList.contains('hidden')) return true
        }
        return false
    }

    it('shows the way to the discovery page at a phone width', () => {
        const view = footer()
        const link = view.find('[data-testid="footer-discovery"]')

        expect(link.exists()).toBe(true)
        expect(link.text()).toContain('Wachen-Verzeichnis')
        expect(hiddenOnPhones(link.element, view.element)).toBe(false)
    })

    it('keeps what a layout puts into the footer for wider screens', () => {
        const view = footer()

        expect(hiddenOnPhones(view.find('[data-testid="switcher"]').element, view.element)).toBe(true)
    })
})
