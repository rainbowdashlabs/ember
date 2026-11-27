/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import FormSection from './FormSection.vue'

function section(props: Record<string, unknown> = {}) {
    return mount(FormSection, {
        props: {title: 'Erinnerungen', summary: 'Erinnerung 1, 7 Tage vorher', ...props},
        slots: {default: '<input data-testid="inside"/>'},
        attachTo: document.body,
    })
}

/** A closed section shows what it holds, keeps its content in the page, and opens on a click or when it needs attention. */
describe('FormSection', () => {
    it('starts closed, says what it holds and keeps its content rendered but hidden', () => {
        const wrapper = section()

        expect(wrapper.get('[data-section-toggle]').attributes('aria-expanded')).toBe('false')
        expect(wrapper.get('[data-section-summary]').text()).toBe('Erinnerung 1, 7 Tage vorher')
        expect(wrapper.find('[data-testid="inside"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="inside"]').isVisible()).toBe(false)
    })

    it('opens and closes on a click', async () => {
        const wrapper = section()

        await wrapper.get('[data-section-toggle]').trigger('click')
        expect(wrapper.get('[data-section-toggle]').attributes('aria-expanded')).toBe('true')
        expect(wrapper.find('[data-testid="inside"]').isVisible()).toBe(true)
        expect(wrapper.emitted('update:open')?.[0]).toEqual([true])

        await wrapper.get('[data-section-toggle]').trigger('click')
        expect(wrapper.find('[data-testid="inside"]').isVisible()).toBe(false)
    })

    it('opens by itself when it asks for attention, also later', async () => {
        expect(section({attention: true}).find('[data-testid="inside"]').isVisible()).toBe(true)

        const wrapper = section()
        await wrapper.setProps({attention: true})
        expect(wrapper.find('[data-testid="inside"]').isVisible()).toBe(true)
    })

    it('stands in a heading, of the third level unless told otherwise, so heading navigation finds it', () => {
        expect(section().find('h3 [data-section-toggle]').exists()).toBe(true)
        expect(section({level: 2}).find('h2 [data-section-toggle]').exists()).toBe(true)
    })
})
