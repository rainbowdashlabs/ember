/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import AppLink from './AppLink.vue'

/**
 * A text link: where it opens and what it sends along.
 *
 * @vitest-environment happy-dom
 */
describe('AppLink', () => {
    function link(props: Record<string, unknown>) {
        return mount(AppLink, {props: {href: 'https://example.org', ...props}, slots: {default: 'Example'}}).find('a')
    }

    it('opens a link leaving the site in a new tab and sends no opener or referrer along', () => {
        const away = link({external: true})

        expect(away.attributes('target')).toBe('_blank')
        expect(away.attributes('rel')).toBe('external noopener noreferrer')
    })

    it('keeps a link leaving the site in the same tab where asked to', () => {
        const away = link({external: true, sameTab: true})

        expect(away.attributes('target')).toBeUndefined()
        expect(away.attributes('rel')).toBe('external noopener noreferrer')
    })

    it('leaves a link within the site as it is', () => {
        const home = link({href: '/admin/first-station'})

        expect(home.attributes('href')).toBe('/admin/first-station')
        expect(home.attributes('target')).toBeUndefined()
        expect(home.attributes('rel')).toBeUndefined()
        expect(home.text()).toBe('Example')
    })
})
