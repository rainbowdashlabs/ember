/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import DockButton from './DockButton.vue'

const RouterLink = {template: '<a :href="String(to)" :target="target"><slot/></a>', props: ['to', 'target']}

function dock(props: Record<string, unknown>, slot?: string) {
    return mount(DockButton, {
        props: {label: 'Hilfe', ...props},
        slots: slot ? {default: slot} : {},
        global: {stubs: {'router-link': RouterLink, AppIcon: {template: '<i class="app-icon"/>', props: ['icon']}}},
    })
}

/** The round buttons in the corner look alike in size and differ only in colour. */
describe('DockButton', () => {
    it('gives every tone the same size, border and box for what it shows', () => {
        const tones = ['neutral', 'error', 'secondary'].map(tone => dock({icon: ['fas', 'bug'], tone}))

        for (const button of tones) {
            expect(button.classes()).toEqual(expect.arrayContaining(['h-10', 'w-10', 'rounded-full', 'border']))
            expect(button.find('span').classes()).toEqual(expect.arrayContaining(['h-5', 'w-5']))
        }
        expect(tones[1]!.classes()).toContain('bg-error')
        expect(tones[0]!.classes()).not.toContain('bg-error')
    })

    it('names what it does for screen readers and on hover', () => {
        const button = dock({icon: ['fas', 'rocket']})

        expect(button.attributes('aria-label')).toBe('Hilfe')
        expect(button.attributes('title')).toBe('Hilfe')
    })

    it('is a link with a target, showing a picture of its own in the same box', () => {
        const button = dock({to: '/help', target: '_blank'}, '<img class="logo"/>')

        expect(button.element.tagName).toBe('A')
        expect(button.attributes('target')).toBe('_blank')
        expect(button.find('span .logo').exists()).toBe(true)
    })

    it('reports a click', async () => {
        const button = dock({icon: ['fas', 'bug']})

        await button.trigger('click')

        expect(button.emitted('click')).toHaveLength(1)
    })
})
