/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import TabsCell from './TabsCell.vue'

const CONFIG = {
    items: [
        {title: 'Anfahrt', body: 'Über die Hauptstraße.'},
        {title: '', body: 'Jeden Dienstag.'},
    ],
}

/**
 * The tabs cell of a public page, read with a keyboard or a screen reader.
 *
 * @vitest-environment happy-dom
 */
describe('TabsCell', () => {
    it('offers its items as tabs, naming an untitled one by its place', () => {
        const wrapper = mount(TabsCell, {props: {config: CONFIG}})

        expect(wrapper.find('[role="tablist"]').exists()).toBe(true)
        expect(wrapper.findAll('[role="tab"]').map(tab => tab.text())).toEqual(['Anfahrt', 'Tab 2'])
        expect(wrapper.findAll('[role="tab"]')[0]!.attributes('aria-selected')).toBe('true')
    })

    it('shows the panel of the tab the arrow keys move to', async () => {
        const wrapper = mount(TabsCell, {props: {config: CONFIG}})

        await wrapper.findAll('[role="tab"]')[0]!.trigger('keydown', {key: 'ArrowRight'})

        const panel = wrapper.find('[role="tabpanel"]')
        expect(panel.text()).toBe('Jeden Dienstag.')
        expect(panel.attributes('aria-label')).toBe('Tab 2')
        expect(wrapper.findAll('[role="tab"]')[1]!.attributes('aria-selected')).toBe('true')
    })
})
